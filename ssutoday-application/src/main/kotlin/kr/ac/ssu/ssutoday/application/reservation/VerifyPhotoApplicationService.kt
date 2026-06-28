package kr.ac.ssu.ssutoday.application.reservation

import kr.ac.ssu.ssutoday.application.reservation.dto.UploadPhotoCommand
import kr.ac.ssu.ssutoday.core.exception.BusinessException
import kr.ac.ssu.ssutoday.core.port.DiscordVerifyPhotoNotificationPort
import kr.ac.ssu.ssutoday.core.port.FileStoragePort
import kr.ac.ssu.ssutoday.core.port.TurnstileVerificationPort
import kr.ac.ssu.ssutoday.core.status.StatusCode
import kr.ac.ssu.ssutoday.core.transaction.afterCommit
import kr.ac.ssu.ssutoday.domain.reservation.ReservationService
import kr.ac.ssu.ssutoday.domain.reservation.VerifyPhotoService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class VerifyPhotoApplicationService(
    private val reservationService: ReservationService,
    private val verifyPhotoService: VerifyPhotoService,
    private val fileStoragePort: FileStoragePort,
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
        val key = "${command.reservationId}/${System.currentTimeMillis()}"
        val uploadedUrl =
            try {
                fileStoragePort.upload(bucket, key, command.contentType, command.size, command.input)
            } catch (exception: Exception) {
                throw RuntimeException("파일 업로드에 실패했습니다", exception)
            }
        val publicUrl = buildPublicUrl(key, uploadedUrl)
        verifyPhotoService.create(command.reservationId, publicUrl)
        afterCommit {
            discordVerifyPhotoNotificationPort.send(
                reservationId = reservation.id,
                adminToken = reservation.adminToken,
                studentId = reservation.studentId,
                roomNo = reservation.roomNo,
                date = reservation.date,
                startBlock = reservation.startBlock,
                endBlock = reservation.endBlock,
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
}
