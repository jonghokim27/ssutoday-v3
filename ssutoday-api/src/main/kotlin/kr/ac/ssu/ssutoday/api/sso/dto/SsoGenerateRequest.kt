package kr.ac.ssu.ssutoday.api.sso.dto

import jakarta.validation.constraints.NotEmpty

data class SsoGenerateRequest(
    @field:NotEmpty val clientId: String,
)
