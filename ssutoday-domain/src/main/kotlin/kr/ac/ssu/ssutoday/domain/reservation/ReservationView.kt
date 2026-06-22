package kr.ac.ssu.ssutoday.domain.reservation

import java.sql.Date
import java.sql.Timestamp

data class ReservationView(
    val id: Long,
    val studentId: Int,
    val roomNo: String,
    val date: Date,
    val startBlock: Int,
    val endBlock: Int,
    val createdAt: Timestamp,
    val deletedAt: Timestamp?,
    val deletedReason: String?,
    val active: Boolean,
)
