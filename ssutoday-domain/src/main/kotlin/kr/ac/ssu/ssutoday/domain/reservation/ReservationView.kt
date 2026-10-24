package kr.ac.ssu.ssutoday.domain.reservation

import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime

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
    val adminToken: String,
    val photoDeleteCount: Int,
) {
    val endAt: LocalDateTime get() = date.atStartOfDay().plusMinutes((endBlock + 1) * 30L)
}
