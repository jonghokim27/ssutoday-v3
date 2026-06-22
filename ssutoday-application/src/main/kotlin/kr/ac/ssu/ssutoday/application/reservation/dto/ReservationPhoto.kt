package kr.ac.ssu.ssutoday.application.reservation.dto

import java.sql.Timestamp

data class ReservationPhoto(
    val idx: Long,
    val reserveIdx: Long,
    val url: String,
    val createdAt: Timestamp,
)
