package kr.ac.ssu.ssutoday.api.reservation.dto

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Pattern

data class AdminReservationRequest(
    @field:NotEmpty
    @field:Pattern(regexp = "reserveCancel|photoDelete|photoExecpt")
    val type: String,
    @field:NotEmpty
    @field:Pattern(regexp = "ios|android")
    val osType: String,
    @field:NotEmpty
    val uuid: String,
    @field:NotEmpty
    val signature: String,
    val idx: Long,
    val text: String?,
)
