package kr.ac.ssu.ssutoday.domain.reservation

import java.sql.Date

data class ReservationRequestView(
    val id: Long,
    val studentId: Int,
    val roomNo: String,
    val date: Date,
    val startBlock: Int,
    val endBlock: Int,
    val status: Int,
)
