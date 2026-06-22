package kr.ac.ssu.ssutoday.api.device.dto

import jakarta.validation.constraints.Pattern

data class DeviceVersionRequest(
    @field:Pattern(regexp = "ios|android") val osType: String,
    @field:Pattern(regexp = "[0-9].[0-9].[0-9]") val version: String,
)
