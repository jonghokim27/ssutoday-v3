package kr.ac.ssu.ssutoday.application.reservation.dto

data class RoomReservation(
    val idx: Long?,
    val studentInfo: String,
    val startBlock: Int,
    val endBlock: Int,
    val isMine: Boolean,
)
