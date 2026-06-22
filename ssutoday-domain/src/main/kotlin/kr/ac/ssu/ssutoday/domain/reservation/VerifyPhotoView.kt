package kr.ac.ssu.ssutoday.domain.reservation

import java.sql.Timestamp

data class VerifyPhotoView(
    val id: Long,
    val reservationId: Long,
    val url: String,
    val createdAt: Timestamp,
)
