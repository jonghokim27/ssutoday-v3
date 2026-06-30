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
        val reservationDateTime =
            ReservationDateTimeFormatter.format(
                reservation.date,
                reservation.startBlock,
                reservation.endBlock,
            )
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

    private fun majorShortName(major: String): String =
        when (major) {
            "cse" -> "컴"
            "media" -> "글"
            "mediamba" -> "미경"
            else -> ""
        }

    private companion object {
        const val VERIFY_PHOTO_FILE_TOKEN_LENGTH = 20
    }
}
