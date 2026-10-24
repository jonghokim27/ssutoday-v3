package kr.ac.ssu.ssutoday.application.penalty

import kr.ac.ssu.ssutoday.application.penalty.dto.GrantPenaltyCommand
import kr.ac.ssu.ssutoday.application.penalty.dto.PenaltyActionOutcome
import kr.ac.ssu.ssutoday.application.penalty.dto.PenaltyActionResult
import kr.ac.ssu.ssutoday.application.penalty.dto.RevokePenaltyCommand
import kr.ac.ssu.ssutoday.application.reservation.ReservationDateTimeFormatter
import kr.ac.ssu.ssutoday.application.reservation.StudentInfoFormatter
import kr.ac.ssu.ssutoday.core.dto.PushMessage
import kr.ac.ssu.ssutoday.core.dto.PushMessages
import kr.ac.ssu.ssutoday.core.penalty.PenaltyReason
import kr.ac.ssu.ssutoday.core.penalty.PenaltyType
import kr.ac.ssu.ssutoday.core.port.DiscordPenaltyNotificationPort
import kr.ac.ssu.ssutoday.core.port.PushMessagePublisher
import kr.ac.ssu.ssutoday.core.transaction.afterCommit
import kr.ac.ssu.ssutoday.domain.penalty.PenaltyGrantRejection
import kr.ac.ssu.ssutoday.domain.penalty.PenaltyPolicy
import kr.ac.ssu.ssutoday.domain.penalty.PenaltyService
import kr.ac.ssu.ssutoday.domain.penalty.PenaltyStanding
import kr.ac.ssu.ssutoday.domain.penalty.PenaltyView
import kr.ac.ssu.ssutoday.domain.reservation.ReservationService
import kr.ac.ssu.ssutoday.domain.reservation.ReservationView
import kr.ac.ssu.ssutoday.domain.room.RoomService
import kr.ac.ssu.ssutoday.domain.student.DeviceService
import kr.ac.ssu.ssutoday.domain.student.StudentService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class PenaltyCommandApplicationService(
    private val penaltyService: PenaltyService,
    private val penaltyPolicy: PenaltyPolicy,
    private val reservationService: ReservationService,
    private val studentService: StudentService,
    private val roomService: RoomService,
    private val deviceService: DeviceService,
    private val pushMessagePublisher: PushMessagePublisher,
    private val discordPenaltyNotificationPort: DiscordPenaltyNotificationPort,
) {
    /**
     * 시스템이 인증샷 미촬영·미인정으로 예약을 취소할 때 부여한다.
     * 시행일 전에 이용한 예약이거나 이미 부여했으면 건너뛰고 false를 반환한다.
     * 학생 푸시는 예약 취소 알림에 합쳐 보내므로 호출한 쪽이 담당한다.
     */
    @Transactional
    fun grantAutomatically(
        reservation: ReservationView,
        reason: PenaltyReason,
    ): Boolean {
        require(reason.type == PenaltyType.PHOTO && !reason.adminSelectable) { "자동 부여 사유가 아닙니다: $reason" }
        val now = LocalDateTime.now()
        if (!penaltyPolicy.applies(reason.type, reservation.date)) return false
        if (penaltyService.existsActive(reservation.id, reason.type)) return false

        val penalty = penaltyService.create(reservation.studentId, reservation.id, reason, SYSTEM_NAME, now)
        val fields = grantFields(reservation, penalty, penaltyService.standing(reservation.studentId, now), cancelledCount = 0)
        afterCommit {
            discordPenaltyNotificationPort.send(GRANT_NOTICE, fields, penalty.adminToken)
        }
        return true
    }

    @Transactional
    fun grantByAdmin(command: GrantPenaltyCommand): PenaltyActionResult {
        val reservation =
            reservationService.findByAdminToken(command.adminToken)
                ?: return PenaltyActionResult(PenaltyActionOutcome.NOT_FOUND)
        val reason =
            command.reason?.takeIf { it.adminSelectable }
                ?: return PenaltyActionResult(PenaltyActionOutcome.INVALID_INPUT)
        val adminName =
            command.adminName?.trim()?.takeIf(String::isNotEmpty)
                ?: return PenaltyActionResult(PenaltyActionOutcome.INVALID_INPUT)
        val now = LocalDateTime.now()
        val rejection =
            penaltyPolicy.grantRejection(
                type = reason.type,
                reservationDate = reservation.date,
                reservationEndAt = reservation.endAt,
                alreadyGranted = penaltyService.existsActive(reservation.id, reason.type),
                now = now,
            )
        if (rejection != null) return PenaltyActionResult(rejection.toOutcome())

        val penalty = penaltyService.create(reservation.studentId, reservation.id, reason, adminName, now)
        val cancelled =
            if (reason.type == PenaltyType.FRAUD) {
                cancelForFraud(reservation, adminName, command.cancelUpcoming, now)
            } else {
                emptyList()
            }
        val standing = penaltyService.standing(reservation.studentId, now)
        val fields = grantFields(reservation, penalty, standing, cancelled.size)
        val roomName = roomName(reservation)
        val title = if (reason.type == PenaltyType.FRAUD) PushMessages.SUSPENSION_TITLE else PushMessages.PENALTY_GRANT_TITLE
        val body =
            if (reason.type == PenaltyType.FRAUD) {
                PushMessages.suspensionBody(cancelled.isNotEmpty(), penalty.expiresAt.toLocalDate(), reason.label)
            } else {
                PushMessages.penaltyGrantBody(reservation.date, roomName)
            }
        val studentId = reservation.studentId
        afterCommit {
            sendPush(studentId, title, body)
            discordPenaltyNotificationPort.send(GRANT_NOTICE, fields, penalty.adminToken)
        }
        return PenaltyActionResult(
            PenaltyActionOutcome.DONE,
            listOfNotNull(
                "대상" to StudentInfoFormatter.format(studentService.get(reservation.studentId)),
                "페널티 종류" to reason.type.label,
                "부여 사유" to reason.label,
                "유효 페널티" to PenaltyTextFormatter.standing(standing),
                "만료 일시" to ReservationDateTimeFormatter.formatDateTime(penalty.expiresAt),
                ("취소된 예약" to "${cancelled.size}건").takeIf { reason.type == PenaltyType.FRAUD },
                "푸시 알림" to "발송 요청 완료",
            ),
        )
    }

    @Transactional
    fun revokeByAdmin(command: RevokePenaltyCommand): PenaltyActionResult {
        val penalty =
            penaltyService.findByAdminToken(command.adminToken)
                ?: return PenaltyActionResult(PenaltyActionOutcome.NOT_FOUND)
        val now = LocalDateTime.now()
        if (penalty.revokedAt != null) return PenaltyActionResult(PenaltyActionOutcome.ALREADY_REVOKED)
        if (!penalty.expiresAt.isAfter(now)) return PenaltyActionResult(PenaltyActionOutcome.EXPIRED)
        val reason =
            command.reason?.trim()?.takeIf(String::isNotEmpty)
                ?: return PenaltyActionResult(PenaltyActionOutcome.INVALID_INPUT)
        val adminName =
            command.adminName?.trim()?.takeIf(String::isNotEmpty)
                ?: return PenaltyActionResult(PenaltyActionOutcome.INVALID_INPUT)

        penaltyService.revoke(penalty.id, adminName, reason, now)
        val standing = penaltyService.standing(penalty.studentId, now)
        val student = studentService.get(penalty.studentId)
        val grantedOn = penalty.grantedAt.toLocalDate()
        val (title, body) =
            when {
                penalty.type == PenaltyType.PHOTO -> PushMessages.PENALTY_REVOKE_TITLE to PushMessages.penaltyRevokeBody(grantedOn)
                standing.suspended -> PushMessages.PENALTY_REVOKE_TITLE to PushMessages.fraudRevokeBody(grantedOn)
                else -> PushMessages.SUSPENSION_LIFT_TITLE to PushMessages.suspensionLiftBody(grantedOn)
            }
        val fields =
            listOf(
                "예약 고유번호" to penalty.reservationId.toString(),
                "예약자" to StudentInfoFormatter.format(student),
                "페널티 종류" to penalty.type.label,
                "부여 사유" to penalty.reason.label,
                "부여 일시" to ReservationDateTimeFormatter.formatDateTime(penalty.grantedAt),
                "취소 사유" to reason,
                "처리자" to adminName,
                "유효 페널티" to PenaltyTextFormatter.standing(standing),
            )
        afterCommit {
            sendPush(penalty.studentId, title, body)
            discordPenaltyNotificationPort.send(REVOKE_NOTICE, fields)
        }
        return PenaltyActionResult(
            PenaltyActionOutcome.DONE,
            listOf(
                "대상" to StudentInfoFormatter.format(student),
                "페널티 종류" to penalty.type.label,
                "유효 페널티" to PenaltyTextFormatter.standing(standing),
                "푸시 알림" to "발송 요청 완료",
            ),
        )
    }

    /** 부정 예약이면 해당 예약을 즉시 취소하고, 요청하면 이용 전인 다른 예약도 취소한다. */
    private fun cancelForFraud(
        reservation: ReservationView,
        adminName: String,
        cancelUpcoming: Boolean,
        now: LocalDateTime,
    ): List<ReservationView> {
        val targets = mutableListOf<ReservationView>()
        if (reservation.active && reservation.endAt.isAfter(now)) targets += reservation
        if (cancelUpcoming) {
            val block = (now.hour * 60 + now.minute) / 30
            targets +=
                reservationService
                    .findUpcoming(reservation.studentId, now.toLocalDate(), block)
                    .filter { it.id != reservation.id }
        }
        targets.forEach { reservationService.cancelByAdmin(it.id, "부정 예약 취소 (처리자: $adminName)") }
        return targets
    }

    private fun grantFields(
        reservation: ReservationView,
        penalty: PenaltyView,
        standing: PenaltyStanding,
        cancelledCount: Int,
    ): List<Pair<String, String>> =
        PenaltyTextFormatter.reservationFields(reservation, studentService.get(reservation.studentId), roomName(reservation)) +
            listOfNotNull(
                "페널티 종류" to penalty.type.label,
                "부여 사유" to penalty.reason.label,
                "처리자" to penalty.grantedBy,
                "유효 페널티" to PenaltyTextFormatter.standing(standing),
                "만료 일시" to ReservationDateTimeFormatter.formatDateTime(penalty.expiresAt),
                ("취소된 예약" to "${cancelledCount}건").takeIf { penalty.type == PenaltyType.FRAUD },
            )

    private fun roomName(reservation: ReservationView): String = roomService.getByNo(reservation.roomNo)?.name ?: reservation.roomNo

    private fun sendPush(
        studentId: Int,
        title: String,
        body: String,
    ) {
        deviceService.findReservationPushTokens(studentId).forEach { token ->
            pushMessagePublisher.publish(
                PushMessage(
                    type = "token",
                    token = token,
                    title = title,
                    body = body,
                    category = PUSH_CATEGORY,
                ),
            )
        }
    }

    private fun PenaltyGrantRejection.toOutcome(): PenaltyActionOutcome =
        when (this) {
            PenaltyGrantRejection.ALREADY_GRANTED -> PenaltyActionOutcome.ALREADY_GRANTED
            PenaltyGrantRejection.NOT_IN_EFFECT -> PenaltyActionOutcome.NOT_IN_EFFECT
            PenaltyGrantRejection.DEADLINE_PASSED -> PenaltyActionOutcome.DEADLINE_PASSED
        }

    companion object {
        /** 앱이 이 카테고리의 알림을 누르면 페널티 현황 화면으로 이동한다. */
        const val PUSH_CATEGORY = "penalty"
        private const val SYSTEM_NAME = "시스템"
        private const val GRANT_NOTICE = "**[페널티 부여 알림]**"
        private const val REVOKE_NOTICE = "**[페널티 취소 알림]**"
    }
}
