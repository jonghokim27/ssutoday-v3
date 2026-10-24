package kr.ac.ssu.ssutoday.application.penalty.dto

import kr.ac.ssu.ssutoday.core.penalty.PenaltyReason
import kr.ac.ssu.ssutoday.core.penalty.PenaltyType
import java.time.LocalDate
import java.time.LocalDateTime

data class PenaltyItem(
    val id: Long,
    val type: PenaltyType,
    val reason: PenaltyReason,
    val grantedAt: LocalDateTime,
    val expiresAt: LocalDateTime,
    val roomName: String,
    val reservationDate: LocalDate,
    val startBlock: Int,
    val endBlock: Int,
    val reservationCancelled: Boolean,
)
