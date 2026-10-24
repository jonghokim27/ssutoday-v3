package kr.ac.ssu.ssutoday.domain.penalty

import kr.ac.ssu.ssutoday.core.penalty.PenaltyReason
import kr.ac.ssu.ssutoday.core.penalty.PenaltyType
import java.time.LocalDateTime

data class PenaltyView(
    val id: Long,
    val studentId: Int,
    val reservationId: Long,
    val type: PenaltyType,
    val reason: PenaltyReason,
    val grantedBy: String,
    val grantedAt: LocalDateTime,
    val expiresAt: LocalDateTime,
    val adminToken: String,
    val revokedAt: LocalDateTime?,
    val revokedBy: String?,
    val revokeReason: String?,
) {
    fun validAt(now: LocalDateTime): Boolean = revokedAt == null && expiresAt.isAfter(now)
}
