package kr.ac.ssu.ssutoday.api.reservation.dto

import kr.ac.ssu.ssutoday.application.reservation.dto.ReservationDetail

data class ReservationListResponse(
    val reserves: List<ReservationDetail>,
    val totalPages: Int,
    val totalElements: Long,
)
