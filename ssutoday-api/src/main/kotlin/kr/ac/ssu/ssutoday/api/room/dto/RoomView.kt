package kr.ac.ssu.ssutoday.api.room.dto

import kr.ac.ssu.ssutoday.application.reservation.dto.RoomReservation

data class RoomView(
    val no: String,
    val name: String,
    val capacity: Int,
    val location: String,
    val tags: String,
    val image: String,
    val bigImage: String,
    val reserves: List<RoomReservation>,
)
