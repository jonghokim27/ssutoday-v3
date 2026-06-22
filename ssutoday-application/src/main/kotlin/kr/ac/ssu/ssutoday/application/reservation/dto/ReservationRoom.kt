package kr.ac.ssu.ssutoday.application.reservation.dto

data class ReservationRoom(
    val no: String,
    val name: String,
    val major: String,
    val capacity: Int,
    val location: String,
    val tags: String,
    val image: String,
    val bigImage: String,
    val isAvailable: Int,
)
