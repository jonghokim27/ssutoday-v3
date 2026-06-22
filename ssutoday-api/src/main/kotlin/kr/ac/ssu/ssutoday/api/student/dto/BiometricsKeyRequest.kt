package kr.ac.ssu.ssutoday.api.student.dto

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Pattern

data class BiometricsKeyRequest(
    @field:Pattern(regexp = "ios|android") val osType: String,
    @field:NotEmpty val uuid: String,
    @field:NotEmpty val publicKey: String,
)
