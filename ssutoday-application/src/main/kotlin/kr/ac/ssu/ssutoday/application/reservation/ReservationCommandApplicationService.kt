package kr.ac.ssu.ssutoday.application.reservation

import kr.ac.ssu.ssutoday.application.reservation.dto.AdminReservationCommand
import kr.ac.ssu.ssutoday.application.reservation.dto.CreateReservationCommand
import kr.ac.ssu.ssutoday.core.exception.BusinessException
import kr.ac.ssu.ssutoday.core.port.ReservationRequestPublisher
import kr.ac.ssu.ssutoday.core.port.RecaptchaVerificationPort
import kr.ac.ssu.ssutoday.core.status.SsuStatus
import kr.ac.ssu.ssutoday.core.transaction.afterCommit
import kr.ac.ssu.ssutoday.domain.config.ConfigService
import kr.ac.ssu.ssutoday.domain.reservation.ReservationCompletionPolicy
import kr.ac.ssu.ssutoday.domain.reservation.ReservationRequestPolicy
import kr.ac.ssu.ssutoday.domain.reservation.ReservationRequestService
import kr.ac.ssu.ssutoday.domain.reservation.ReservationService
import kr.ac.ssu.ssutoday.domain.reservation.VerifyPhotoService
import kr.ac.ssu.ssutoday.domain.room.RoomService
import kr.ac.ssu.ssutoday.domain.student.StudentService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Base64

@Service
class ReservationCommandApplicationService(
    private val reservationRequestService: ReservationRequestService,
    private val reservationService: ReservationService,
    private val verifyPhotoService: VerifyPhotoService,
    private val roomService: RoomService,
    private val studentService: StudentService,
    private val configService: ConfigService,
    private val reservationRequestPolicy: ReservationRequestPolicy,
    private val reservationCompletionPolicy: ReservationCompletionPolicy,
    private val reservationRequestPublisher: ReservationRequestPublisher,
    private val recaptchaVerificationPort: RecaptchaVerificationPort,
) {
    @Transactional
    fun request(command: CreateReservationCommand): Long {
        try {
            if (!recaptchaVerificationPort.verify(command.recaptchaToken, RECAPTCHA_ACTION)) {
                throw BusinessException(SsuStatus.SSU4003)
            }
            reservationService.validate(command.startBlock, command.endBlock)
            val room = roomService.get(command.roomNo, command.major, command.admin)
            if (!room.isAvailable) {
                throw BusinessException(SsuStatus.SSU5091)
            }
            if (configService.isReservationRequestDisabled()) {
                throw BusinessException(SsuStatus.SSU5091)
            }
            val requestId = reservationRequestService.create(
                studentId = command.studentId,
                roomNo = command.roomNo,
                date = command.date,
                startBlock = command.startBlock,
                endBlock = command.endBlock,
            )
            afterCommit {
                try {
                    reservationRequestPublisher.publish(requestId)
                } catch (exception: Exception) {
                    throw BusinessException(SsuStatus.SSU5090, cause = exception)
                }
            }
            return requestId
        } catch (exception: BusinessException) {
            if (
                exception.status == SsuStatus.SSU4000 ||
                exception.status == SsuStatus.SSU4003 ||
                exception.status == SsuStatus.SSU5090 ||
                exception.status == SsuStatus.SSU5091
            ) {
                throw exception
            }
            throw BusinessException(SsuStatus.SSU5090, cause = exception)
        } catch (exception: Exception) {
            throw BusinessException(SsuStatus.SSU5090, cause = exception)
        }
    }

    @Transactional
    fun cancel(studentId: Int, reservationId: Long, reason: String = "사용자 취소") =
        reservationService.cancel(studentId, reservationId, reason)

    @Transactional
    fun process(requestId: Long) {
        val request = reservationRequestService.get(requestId)
        val student = studentService.get(request.studentId)
        val rejectionStatus = reservationRequestPolicy.rejectionStatus(
            request = request,
            admin = student.isAdmin,
            roomConflict = reservationService.hasRoomConflict(
                request.roomNo,
                request.date,
                request.startBlock,
                request.endBlock,
            ),
            reservedBlocks = reservationService.totalReservedBlocks(request.studentId, request.date),
            studentConflict = reservationService.hasStudentConflict(
                request.studentId,
                request.date,
                request.startBlock,
                request.endBlock,
            ),
            now = LocalDateTime.now(SEOUL),
        )
        if (rejectionStatus != null) {
            reservationRequestService.updateStatus(requestId, rejectionStatus)
            return
        }

        reservationService.create(
            studentId = request.studentId,
            roomNo = request.roomNo,
            date = request.date,
            startBlock = request.startBlock,
            endBlock = request.endBlock,
        )
        reservationRequestService.accept(requestId)
    }

    @Transactional
    fun done(studentId: Int, reservationId: Long) {
        val reservation = reservationService.getActive(studentId, reservationId, SsuStatus.SSU4230)
        val verifyPhotoSatisfied =
            reservationService.isContinuous(reservation) || verifyPhotoService.find(reservationId) != null
        val completionBlock = reservationCompletionPolicy.completionBlock(
            reservation,
            verifyPhotoSatisfied,
            LocalDateTime.now(SEOUL),
        )
        reservationService.finish(reservationId, completionBlock)
    }

    @Transactional
    fun admin(command: AdminReservationCommand): Int {
        val reservation = reservationService.find(command.reservationId) ?: return 0
        if (!reservation.active) return 1

        val endAt = reservation.date.toLocalDate().atStartOfDay()
            .plusMinutes((reservation.endBlock + 1) * 30L)
        if (endAt.isBefore(LocalDateTime.now(SEOUL))) return 2

        val publicKey = studentService.findBiometricsPublicKey(
            command.administratorId,
            command.osType,
            command.uuid,
        ) ?: return 99
        if (!isValidSignature(command, publicKey)) return 99

        return when (command.type) {
            ADMIN_CANCEL -> {
                val reason = command.text?.takeIf(String::isNotEmpty) ?: return 99
                reservationService.cancelByAdmin(
                    command.reservationId,
                    "관리자 취소 (사유: $reason)",
                )
                3
            }
            PHOTO_DELETE -> {
                if (!verifyPhotoService.delete(command.reservationId)) return 4
                reservationService.resetCreatedAt(command.reservationId)
                5
            }
            PHOTO_EXCEPT -> {
                if (!verifyPhotoService.createException(command.reservationId)) return 6
                7
            }
            else -> 99
        }
    }

    private fun isValidSignature(command: AdminReservationCommand, publicKey: String): Boolean =
        runCatching {
            val key = KeyFactory.getInstance("RSA").generatePublic(
                X509EncodedKeySpec(Base64.getDecoder().decode(publicKey)),
            )
            Signature.getInstance("SHA256withRSA").run {
                initVerify(key)
                update("${command.type}:${command.reservationId}".toByteArray())
                verify(Base64.getDecoder().decode(command.signature))
            }
        }.getOrDefault(false)

    private companion object {
        val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
        const val ADMIN_CANCEL = "reserveCancel"
        const val PHOTO_DELETE = "photoDelete"
        const val PHOTO_EXCEPT = "photoExecpt"
        const val RECAPTCHA_ACTION = "reservation_request"
    }
}
