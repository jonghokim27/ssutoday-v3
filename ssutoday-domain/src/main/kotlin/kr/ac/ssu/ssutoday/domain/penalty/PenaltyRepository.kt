package kr.ac.ssu.ssutoday.domain.penalty

import org.springframework.data.jpa.repository.JpaRepository
import java.sql.Timestamp

interface PenaltyRepository : JpaRepository<Penalty, Long> {
    fun findAllByStudentIdAndRevokedAtIsNullAndExpiresAtAfterOrderByGrantedAtDesc(
        studentId: Int,
        now: Timestamp,
    ): List<Penalty>

    fun existsByReservationIdAndTypeAndRevokedAtIsNull(
        reservationId: Long,
        type: String,
    ): Boolean

    fun findByAdminToken(adminToken: String): Penalty?
}
