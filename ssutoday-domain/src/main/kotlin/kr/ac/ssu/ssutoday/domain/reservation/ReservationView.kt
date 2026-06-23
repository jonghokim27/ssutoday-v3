package kr.ac.ssu.ssutoday.domain.reservation

import java.sql.Timestamp
import java.time.LocalDate

data class ReservationView(
    val id: Long,
    val studentId: Int,
    val roomNo: String,
    val date: LocalDate,
    val startBlock: Int,
    val endBlock: Int,
    val createdAt: Timestamp,
    val deletedAt: Timestamp?,
    val deletedReason: String?,
    val active: Boolean,
)
