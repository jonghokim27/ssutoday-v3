package kr.ac.ssu.ssutoday.api.device.dto

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Pattern

data class DeviceRegisterRequest(
    @field:Pattern(regexp = "ios|android") val osType: String,
    @field:NotEmpty val uuid: String,
    @field:NotEmpty val pushToken: String,
)
