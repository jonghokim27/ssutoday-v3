package kr.ac.ssu.ssutoday.application.sso.dto

data class SsoGenerationResult(
    val token: String,
    val callbackUrl: String,
)
