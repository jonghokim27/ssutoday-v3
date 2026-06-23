package kr.ac.ssu.ssutoday.domain.reservation

import java.time.LocalDate

data class ReservationRequestView(
    val id: Long,
    val studentId: Int,
    val roomNo: String,
    val date: LocalDate,
    val startBlock: Int,
    val endBlock: Int,
    val status: Int,
)
