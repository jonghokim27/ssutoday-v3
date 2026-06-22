package kr.ac.ssu.ssutoday.api.reservation.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

data class ReservationListRequest(
    @field:Min(0) val page: Int,
    @field:Min(0) @field:Max(1) val type: Int,
)
