package kr.ac.ssu.ssutoday.domain.penalty

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
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

    // 같은 페널티의 취소 요청이 동시에 들어와도 한 번만 처리하도록 행을 잠근다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Penalty p where p.adminToken = :adminToken")
    fun findByAdminTokenForUpdate(adminToken: String): Penalty?
}
