package kr.ac.ssu.ssutoday.api.device.dto

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Pattern

data class DeviceOptionRequest(
    @field:Pattern(regexp = "ios|android") val osType: String,
    @field:NotEmpty val uuid: String,
    @field:Pattern(regexp = "notice|reserve|lms") val option: String,
    val value: Boolean,
)
