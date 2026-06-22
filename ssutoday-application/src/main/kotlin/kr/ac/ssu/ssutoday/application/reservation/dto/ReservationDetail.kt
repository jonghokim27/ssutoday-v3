package kr.ac.ssu.ssutoday.application.reservation.dto

import java.sql.Date
import java.sql.Timestamp

data class ReservationDetail(
    val idx: Long,
    val roomNo: String,
    val date: Date,
    val startBlock: Int,
    val endBlock: Int,
    val createdAt: Timestamp,
    val deletedAt: Timestamp?,
    val deletedReason: String?,
    val roomByRoomNo: ReservationRoom,
    val verifyPhotosByIdx: List<ReservationPhoto>,
    val isContinuous: Boolean,
)
