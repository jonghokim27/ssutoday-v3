package kr.ac.ssu.ssutoday.domain.reservation

import java.time.LocalDate
import java.time.LocalDateTime

data class ReservationRequestView(
    val id: Long,
    val studentId: Int,
    val roomNo: String,
    val date: LocalDate,
    val startBlock: Int,
    val endBlock: Int,
    val status: Int,
    val createdAt: LocalDateTime,
)
