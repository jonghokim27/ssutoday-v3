package kr.ac.ssu.ssutoday.application.penalty

import kr.ac.ssu.ssutoday.application.penalty.dto.PenaltyActionOutcome
import kr.ac.ssu.ssutoday.application.penalty.dto.PenaltyGrantForm
import kr.ac.ssu.ssutoday.application.penalty.dto.PenaltyGrantOption
import kr.ac.ssu.ssutoday.application.penalty.dto.PenaltyItem
import kr.ac.ssu.ssutoday.application.penalty.dto.PenaltyRevokeForm
import kr.ac.ssu.ssutoday.application.penalty.dto.PenaltyStatusResult
import kr.ac.ssu.ssutoday.application.reservation.ReservationDateTimeFormatter
import kr.ac.ssu.ssutoday.application.reservation.StudentInfoFormatter
import kr.ac.ssu.ssutoday.core.exception.BusinessException
import kr.ac.ssu.ssutoday.core.penalty.PenaltyType
import kr.ac.ssu.ssutoday.core.status.StatusCode
import kr.ac.ssu.ssutoday.domain.penalty.PenaltyPolicy
import kr.ac.ssu.ssutoday.domain.penalty.PenaltyService
import kr.ac.ssu.ssutoday.domain.penalty.PenaltyView
import kr.ac.ssu.ssutoday.domain.reservation.ReservationService
import kr.ac.ssu.ssutoday.domain.reservation.ReservationView
import kr.ac.ssu.ssutoday.domain.reservation.VerifyPhotoService
import kr.ac.ssu.ssutoday.domain.room.RoomService
import kr.ac.ssu.ssutoday.domain.student.StudentService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class PenaltyQueryApplicationService(
    private val penaltyService: PenaltyService,
    private val penaltyPolicy: PenaltyPolicy,
    private val reservationService: ReservationService,
    private val verifyPhotoService: VerifyPhotoService,
    private val studentService: StudentService,
    private val roomService: RoomService,
) {
    @Transactional(readOnly = true)
    fun getStatus(studentId: Int): PenaltyStatusResult {
        val now = LocalDateTime.now()
        val penalties = penaltyService.findValid(studentId, now)
        val standing = penaltyPolicy.standing(penalties, now)
        return PenaltyStatusResult(
            applicable = penaltyPolicy.targets(studentService.get(studentId).major),
            photoCount = standing.photoCount,
            maxDailyBlocks = standing.maxDailyBlocks,
            suspendedUntil = standing.suspendedUntil,
            fraud = standing.fraud,
            nextRelief = standing.nextRelief,
            penalties = penalties.map(::toItem),
        )
    }

    /** 예약 관리자 토큰으로 페널티 부여 화면에 필요한 정보를 모은다. 예약이 없으면 null이다. */
    @Transactional(readOnly = true)
    fun getGrantForm(adminToken: String): PenaltyGrantForm? {
        val reservation = reservationService.findByAdminToken(adminToken) ?: return null
        val now = LocalDateTime.now()
        val penalties = penaltyService.findValid(reservation.studentId, now)
        val student = studentService.get(reservation.studentId)
        val block = (now.hour * 60 + now.minute) / 30
        return PenaltyGrantForm(
            details =
                listOf(
                    "예약자" to StudentInfoFormatter.format(student),
                    "시설명" to roomName(reservation),
                    "이용 일시" to ReservationDateTimeFormatter.format(reservation.date, reservation.startBlock, reservation.endBlock),
                    "예약 상태" to (reservation.deletedReason ?: "정상"),
                ),
            photoUrl = verifyPhotoService.find(reservation.id)?.url,
            current = penaltyPolicy.standing(penalties, now),
            photo = option(student.major, reservation, penalties, PenaltyType.PHOTO, now),
            fraud = option(student.major, reservation, penalties, PenaltyType.FRAUD, now),
            upcomingReservations =
                reservationService
                    .findUpcoming(reservation.studentId, now.toLocalDate(), block)
                    .filter { it.id != reservation.id }
                    .map { "${ReservationDateTimeFormatter.format(it.date, it.startBlock, it.endBlock)} ${roomName(it)}" },
        )
    }

    /** 앱 관리자 도구에서 부여 화면을 열 수 있도록 예약의 관리자 토큰을 돌려준다. 관리자 확인은 호출한 쪽이 한다. */
    @Transactional(readOnly = true)
    fun getGrantToken(reservationId: Long): String =
        reservationService.find(reservationId)?.adminToken ?: throw BusinessException(StatusCode.SSU4260)

    /** 페널티 관리자 토큰으로 취소 화면에 필요한 정보를 모은다. 페널티가 없으면 null이다. */
    @Transactional(readOnly = true)
    fun getRevokeForm(adminToken: String): PenaltyRevokeForm? {
        val penalty = penaltyService.findByAdminToken(adminToken) ?: return null
        val now = LocalDateTime.now()
        val penalties = penaltyService.findValid(penalty.studentId, now)
        val student = studentService.get(penalty.studentId)
        val unavailable =
            when {
                penalty.revokedAt != null -> PenaltyActionOutcome.ALREADY_REVOKED
                !penalty.expiresAt.isAfter(now) -> PenaltyActionOutcome.EXPIRED
                else -> null
            }
        return PenaltyRevokeForm(
            details =
                listOf(
                    "대상" to StudentInfoFormatter.format(student),
                    "페널티 종류" to penalty.type.label,
                    "부여 사유" to penalty.reason.label,
                    "부여 일시" to ReservationDateTimeFormatter.formatDateTime(penalty.grantedAt),
                    "처리자" to penalty.grantedBy,
                ),
            current = penaltyPolicy.standing(penalties, now),
            after = penaltyPolicy.standing(penalties.filter { it.id != penalty.id }, now),
            unavailable = unavailable,
        )
    }

    private fun option(
        major: String,
        reservation: ReservationView,
        penalties: List<PenaltyView>,
        type: PenaltyType,
        now: LocalDateTime,
    ) = PenaltyGrantOption(
        rejection =
            penaltyPolicy.grantRejection(
                major = major,
                type = type,
                reservationDate = reservation.date,
                alreadyGranted = penaltyService.existsActive(reservation.id, type),
            ),
        after = penaltyPolicy.standingAfterGrant(penalties, type, now),
    )

    private fun toItem(penalty: PenaltyView): PenaltyItem {
        val reservation =
            reservationService.find(penalty.reservationId)
                ?: throw IllegalStateException("Penalty ${penalty.id} references missing reservation ${penalty.reservationId}")
        return PenaltyItem(
            id = penalty.id,
            type = penalty.type,
            reason = penalty.reason,
            grantedAt = penalty.grantedAt,
            expiresAt = penalty.expiresAt,
            roomName = roomName(reservation),
            reservationDate = reservation.date,
            startBlock = reservation.startBlock,
            endBlock = reservation.endBlock,
            reservationCancelled = !reservation.active,
        )
    }

    private fun roomName(reservation: ReservationView): String = roomService.getByNo(reservation.roomNo)?.name ?: reservation.roomNo
}
