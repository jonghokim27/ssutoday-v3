package kr.ac.ssu.ssutoday.application.reservation.dto

data class ReservationPageResult(
    val reservations: List<ReservationDetail>,
    val totalPages: Int,
    val totalElements: Long
)
