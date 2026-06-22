package kr.ac.ssu.ssutoday.api.sso.dto

import jakarta.validation.constraints.NotEmpty

data class SsoValidateRequest(
    @field:NotEmpty val ssoToken: String,
)
