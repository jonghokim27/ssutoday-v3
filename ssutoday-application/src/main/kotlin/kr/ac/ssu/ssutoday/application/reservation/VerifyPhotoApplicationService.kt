package kr.ac.ssu.ssutoday.application.reservation

import kr.ac.ssu.ssutoday.application.reservation.dto.UploadPhotoCommand
import kr.ac.ssu.ssutoday.core.exception.BusinessException
import kr.ac.ssu.ssutoday.core.port.DiscordVerifyPhotoNotificationPort
import kr.ac.ssu.ssutoday.core.port.FileStoragePort
import kr.ac.ssu.ssutoday.core.port.TokenPort
import kr.ac.ssu.ssutoday.core.port.TurnstileVerificationPort
import kr.ac.ssu.ssutoday.core.status.StatusCode
import kr.ac.ssu.ssutoday.core.transaction.afterCommit
import kr.ac.ssu.ssutoday.domain.reservation.ReservationService
import kr.ac.ssu.ssutoday.domain.reservation.VerifyPhotoService
import kr.ac.ssu.ssutoday.domain.room.RoomService
import kr.ac.ssu.ssutoday.domain.student.StudentService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

@Service
class VerifyPhotoApplicationService(
    private val reservationService: ReservationService,
    private val verifyPhotoService: VerifyPhotoService,
    private val studentService: StudentService,
    private val roomService: RoomService,
    private val fileStoragePort: FileStoragePort,
    private val tokenPort: TokenPort,
    private val turnstileVerificationPort: TurnstileVerificationPort,
    private val discordVerifyPhotoNotificationPort: DiscordVerifyPhotoNotificationPort,
    @Value("\${ssutoday.storage.verify-photo-bucket}")
    private val bucket: String,
    @Value("\${ssutoday.storage.public-base-url:}")
    private val publicBaseUrl: String,
) {
    @Transactional
    fun upload(command: UploadPhotoCommand): String {
        if (!turnstileVerificationPort.verify(command.turnstileToken)) {
            throw BusinessException(StatusCode.SSU4205)
        }
        val reservation = reservationService.getForPhotoUpload(command.studentId, command.reservationId)
        val key = "verifyPhoto/${tokenPort.randomToken(VERIFY_PHOTO_FILE_TOKEN_LENGTH)}.jpeg"
        val uploadedUrl =
            try {
                fileStoragePort.upload(bucket, key, command.contentType, command.size, command.input)
            } catch (exception: Exception) {
                throw RuntimeException("파일 업로드에 실패했습니다", exception)
            }
        val publicUrl = buildPublicUrl(key, uploadedUrl)
        verifyPhotoService.create(command.reservationId, publicUrl)
        val student = studentService.get(reservation.studentId)
        val roomName = roomService.getByNo(reservation.roomNo)?.name ?: reservation.roomNo
        val studentInfo = buildStudentInfo(student.name, student.id, student.major)
        val reservationDateTime = buildReservationDateTime(reservation.date, reservation.startBlock, reservation.endBlock)
        afterCommit {
            discordVerifyPhotoNotificationPort.send(
                content = "**[인증샷 촬영 알림]**",
                reservationId = reservation.id,
                adminToken = reservation.adminToken,
                studentInfo = studentInfo,
                roomName = roomName,
                reservationDateTime = reservationDateTime,
                photoUrl = publicUrl,
            )
        }
        return publicUrl
    }

    private fun buildPublicUrl(
        key: String,
        fallbackUrl: String,
    ): String {
        if (publicBaseUrl.isBlank()) return fallbackUrl

        return "${publicBaseUrl.trimEnd('/')}/${key.trimStart('/')}"
    }

    private fun buildStudentInfo(
        name: String,
        studentId: Int,
        major: String,
    ): String = "$name ($studentId/${majorShortName(major)})"

    private fun buildReservationDateTime(
        date: java.time.LocalDate,
        startBlock: Int,
        endBlock: Int,
    ): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul")).apply {
            set(date.year, date.monthValue - 1, date.dayOfMonth, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val dateText = SimpleDateFormat("yyyy년 MM월 dd일").apply {
            timeZone = TimeZone.getTimeZone("Asia/Seoul")
        }.format(calendar.time)
        val day = DAYS[calendar.get(Calendar.DAY_OF_WEEK) - 1]

        return "$dateText($day) ${blockToTime(startBlock)} ~ ${blockToTime(endBlock + 1)}"
    }

    private fun blockToTime(block: Int): String {
        val totalMinutes = block * 30
        return "%02d:%02d".format(totalMinutes / 60, totalMinutes % 60)
    }

    private fun majorShortName(major: String): String =
        when (major) {
            "cse" -> "컴"
            "sw" -> "솦"
            "media" -> "글"
            "mediamba" -> "미경"
            "sec" -> "정"
            else -> ""
        }

    private companion object {
        const val VERIFY_PHOTO_FILE_TOKEN_LENGTH = 20
        val DAYS = arrayOf("일", "월", "화", "수", "목", "금", "토")
    }
}
