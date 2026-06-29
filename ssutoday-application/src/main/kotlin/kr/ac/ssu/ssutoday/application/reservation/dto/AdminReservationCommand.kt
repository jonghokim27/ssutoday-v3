package kr.ac.ssu.ssutoday.application.reservation.dto

data class AdminReservationCommand(
    val adminName: String,
    val type: String,
    val osType: String,
    val uuid: String,
    val signature: String,
    val reservationId: Long,
    val text: String?,
)
