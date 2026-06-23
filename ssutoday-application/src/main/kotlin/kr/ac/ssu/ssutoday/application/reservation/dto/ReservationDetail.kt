package kr.ac.ssu.ssutoday.application.reservation.dto

import java.sql.Timestamp
import java.time.LocalDate

data class ReservationDetail(
    val idx: Long,
    val roomNo: String,
    val date: LocalDate,
    val startBlock: Int,
    val endBlock: Int,
    val createdAt: Timestamp,
    val deletedAt: Timestamp?,
    val deletedReason: String?,
    val roomByRoomNo: ReservationRoom,
    val verifyPhotosByIdx: List<ReservationPhoto>,
    val isContinuous: Boolean,
)
