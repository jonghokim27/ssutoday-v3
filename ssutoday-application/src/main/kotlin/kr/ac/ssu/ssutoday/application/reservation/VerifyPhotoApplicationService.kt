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
    @Value("\${ssutoday.storage.verify-photo-bucket:ssutoday-reserve-verify-photo}")
    private val bucket: String,
) {
    @Transactional
    fun upload(command: UploadPhotoCommand): String {
        if (!turnstileVerificationPort.verify(command.turnstileToken)) {
            throw BusinessException(StatusCode.SSU4003)
        }
        val reservation = reservationService.getForPhotoUpload(command.studentId, command.reservationId)
        val key = "${command.reservationId}/${System.currentTimeMillis()}"
        val url =
            try {
                fileStoragePort.upload(bucket, key, command.contentType, command.size, command.input)
            } catch (exception: Exception) {
                throw BusinessException(StatusCode.SSU5200, cause = exception)
            }
        verifyPhotoService.create(command.reservationId, url)
        afterCommit {
            discordVerifyPhotoNotificationPort.send(
                reservationId = reservation.id,
                adminToken = reservation.adminToken,
                studentId = reservation.studentId,
                roomNo = reservation.roomNo,
                date = reservation.date,
                startBlock = reservation.startBlock,
                endBlock = reservation.endBlock,
                photoUrl = url,
            )
        }
        return url
    }
}
