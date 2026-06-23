package kr.ac.ssu.ssutoday.api.reservation.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Pattern

data class CreateReservationRequest(
    @field:NotEmpty
    val recaptchaToken: String,
    @field:NotEmpty val roomNo: String,
    @field:Pattern(regexp = "202[3-9]-(0[1-9]|1[0-2])-(0[1-9]|1[0-9]|2[0-9]|3[0-1])")
    val date: String,
    @field:Min(12) @field:Max(43) val startBlock: Int,
    @field:Min(12) @field:Max(43) val endBlock: Int,
)
