package kr.ac.ssu.ssutoday.domain.penalty

import kr.ac.ssu.ssutoday.core.penalty.PenaltyReason
import kr.ac.ssu.ssutoday.core.penalty.PenaltyType
import kr.ac.ssu.ssutoday.domain.penalty.factory.toView
import org.springframework.stereotype.Service
import java.sql.Timestamp
import java.time.LocalDateTime

@Service
class PenaltyService(
    private val repository: PenaltyRepository,
    private val policy: PenaltyPolicy,
) {
    fun create(
        studentId: Int,
        reservationId: Long,
        reason: PenaltyReason,
        grantedBy: String,
        now: LocalDateTime,
    ): PenaltyView =
        repository
            .save(
                Penalty(
                    studentId = studentId,
                    reservationId = reservationId,
                    type = reason.type.name,
                    reason = reason.name,
                    grantedBy = grantedBy,
                    grantedAt = Timestamp.valueOf(now),
                    expiresAt = Timestamp.valueOf(policy.expiresAt(reason.type, now)),
                ),
            ).toView()

    /** 유효한 페널티를 최근 부여 순으로 반환한다. */
    fun findValid(
        studentId: Int,
        now: LocalDateTime,
    ): List<PenaltyView> =
        repository
            .findAllByStudentIdAndRevokedAtIsNullAndExpiresAtAfterOrderByGrantedAtDesc(studentId, Timestamp.valueOf(now))
            .map(Penalty::toView)

    fun standing(
        studentId: Int,
        now: LocalDateTime,
    ): PenaltyStanding = policy.standing(findValid(studentId, now), now)

    /** 같은 예약에 같은 종류의 페널티가 이미 있는지. 만료된 페널티도 포함해 이중 부여를 막는다. */
    fun existsActive(
        reservationId: Long,
        type: PenaltyType,
    ): Boolean = repository.existsByReservationIdAndTypeAndRevokedAtIsNull(reservationId, type.name)

    fun findByAdminToken(adminToken: String): PenaltyView? = repository.findByAdminToken(adminToken)?.toView()

    fun revoke(
        penaltyId: Long,
        revokedBy: String,
        revokeReason: String,
        now: LocalDateTime,
    ) {
        val penalty =
            repository.findById(penaltyId).orElseThrow {
                IllegalStateException("Penalty $penaltyId disappeared")
            }
        penalty.revoke(revokedBy, revokeReason, Timestamp.valueOf(now))
    }
}
