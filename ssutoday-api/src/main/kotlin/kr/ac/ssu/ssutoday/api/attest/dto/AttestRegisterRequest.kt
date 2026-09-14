package kr.ac.ssu.ssutoday.api.attest.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class AttestRegisterRequest(
    @field:NotBlank @field:Size(max = 44) val keyId: String,
    @field:NotBlank @field:Size(max = 43) val challenge: String,
    @field:NotBlank @field:Size(max = 65536) val attestation: String,
)
