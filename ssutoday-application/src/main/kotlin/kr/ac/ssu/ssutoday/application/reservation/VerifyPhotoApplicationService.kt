package kr.ac.ssu.ssutoday.application.reservation

import io.github.oshai.kotlinlogging.KotlinLogging
import kr.ac.ssu.ssutoday.application.attest.AttestationVerificationApplicationService
import kr.ac.ssu.ssutoday.application.attest.dto.VerifyPhotoAttestationCommand
import kr.ac.ssu.ssutoday.application.reservation.dto.UploadPhotoCommand
import kr.ac.ssu.ssutoday.core.dto.PhotoInspection
import kr.ac.ssu.ssutoday.core.exception.BusinessException
import kr.ac.ssu.ssutoday.core.port.DiscordReservationActionNotificationPort
import kr.ac.ssu.ssutoday.core.port.DiscordVerifyPhotoNotificationPort
import kr.ac.ssu.ssutoday.core.port.FileStoragePort
import kr.ac.ssu.ssutoday.core.port.TokenPort
import kr.ac.ssu.ssutoday.core.port.TurnstileVerificationPort
import kr.ac.ssu.ssutoday.core.port.VerifyPhotoInspectionPort
import kr.ac.ssu.ssutoday.core.port.VerifyPhotoInspectionPublisher
import kr.ac.ssu.ssutoday.core.status.StatusCode
import kr.ac.ssu.ssutoday.core.transaction.afterCommit
import kr.ac.ssu.ssutoday.domain.reservation.ReservationService
import kr.ac.ssu.ssutoday.domain.reservation.ReservationView
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
    private val reservationCommandApplicationService: ReservationCommandApplicationService,
    private val fileStoragePort: FileStoragePort,
    private val tokenPort: TokenPort,
    private val turnstileVerificationPort: TurnstileVerificationPort,
    private val discordVerifyPhotoNotificationPort: DiscordVerifyPhotoNotificationPort,
    private val discordReservationActionNotificationPort: DiscordReservationActionNotificationPort,
    private val verifyPhotoInspectionPublisher: VerifyPhotoInspectionPublisher,
    private val verifyPhotoInspectionPort: VerifyPhotoInspectionPort,
    private val attestationVerificationApplicationService: AttestationVerificationApplicationService,
    @Value("\${ssutoday.storage.verify-photo-bucket}")
    private val bucket: String,
    @Value("\${ssutoday.storage.public-base-url:}")
    private val publicBaseUrl: String,
    @Value("\${ssutoday.gemini.enforce}")
    private val enforce: Boolean,
    @Value("\${ssutoday.gemini.reject-threshold}")
    private val rejectThreshold: Double,
) {
    private val log = KotlinLogging.logger {}

    @Transactional
    fun upload(command: UploadPhotoCommand): String {
        if (!turnstileVerificationPort.verify(command.turnstileToken)) {
            throw BusinessException(StatusCode.SSU4205)
        }
        val reservation = reservationService.getForPhotoUpload(command.studentId, command.reservationId)
        val photo = command.input.readBytes()
        val attestation =
            attestationVerificationApplicationService.verify(
                VerifyPhotoAttestationCommand(command.studentId, command.reservationId, photo, command.attestation),
            )
        val key = "verifyPhoto/${tokenPort.randomToken(VERIFY_PHOTO_FILE_TOKEN_LENGTH)}.jpeg"
        val uploadedUrl =
            try {
                photo.inputStream().use { input ->
                    fileStoragePort.upload(bucket, key, command.contentType, photo.size.toLong(), input)
                }
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
                content =
                    "**[인증샷 촬영 알림]**\n앱 무결성: ${attestation.verdict} (${attestation.platform}, " +
                        "${if (attestation.enforced) "강제 모드" else "관찰 모드"})",
                reservationId = reservation.id,
                adminToken = reservation.adminToken,
                studentInfo = studentInfo,
                roomName = roomName,
                reservationDateTime = reservationDateTime,
                photoUrl = publicUrl,
            )
            publishInspection(reservation.id)
        }
        return publicUrl
    }

    private fun publishInspection(reservationId: Long) {
        // 검사 발행 실패가 업로드 성공을 되돌리면 안 된다. 검사는 부가 기능이다.
        runCatching { verifyPhotoInspectionPublisher.publish(reservationId) }
            .onFailure { log.error(it) { "인증샷 자동 검사 요청 발행에 실패했습니다: $reservationId" } }
    }

    /**
     * consumer가 호출한다. 인증샷을 Gemini로 검사해 결과를 Discord에 남기고,
     * enforce가 켜져 있으면 첫 거부는 인증샷 삭제, 두 번째 거부는 예약 취소로 처리한다.
     *
     * 판정 불가(null)와 낮은 confidence는 통과시킨다. 정상 이용자를 막는 쪽이 더 큰 손해다.
     */
    fun inspect(reservationId: Long) {
        val reservation = reservationService.find(reservationId) ?: return
        if (!reservation.active) return

        val photo = verifyPhotoService.find(reservationId) ?: return
        if (photo.url.endsWith(EXCEPTION_PHOTO_SUFFIX)) return

        val inspection = verifyPhotoInspectionPort.inspect(photo.url) ?: return

        notifyInspection(reservation, photo.url, inspection)

        if (!enforce) return
        if (inspection.isStudyRoom) return
        if (inspection.confidence < rejectThreshold) return

        val result =
            reservationCommandApplicationService.rejectVerifyPhotoByInspection(
                reservationId = reservationId,
                inspectionReason = inspection.reason,
            )
        log.info {
            "인증샷 자동 거부: reservationId=$reservationId action=${result.action} " +
                "count=${result.deleteCount} status=${result.status}"
        }
    }

    private fun notifyInspection(
        reservation: ReservationView,
        photoUrl: String,
        inspection: PhotoInspection,
    ) {
        val student = studentService.get(reservation.studentId)
        val roomName = roomService.getByNo(reservation.roomNo)?.name ?: reservation.roomNo
        val label = if (inspection.isStudyRoom) "인정" else "비인정"
        val mode = if (enforce) "" else " · 관찰 모드(조치 없음)"
        discordReservationActionNotificationPort.send(
            content = "**[인증샷 자동 검사]**",
            reservationId = reservation.id,
            studentInfo = buildStudentInfo(student.name, student.id, student.major),
            roomName = roomName,
            reservationDateTime =
                ReservationDateTimeFormatter.format(
                    reservation.date,
                    reservation.startBlock,
                    reservation.endBlock,
                ),
            actionFieldName = "자동 검사 결과",
            actionFieldValue = "$label (confidence ${inspection.confidence})$mode\n${inspection.reason}",
            photoUrl = photoUrl,
        )
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

        const val EXCEPTION_PHOTO_SUFFIX = "except.png"
    }
}
