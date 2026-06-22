package kr.ac.ssu.ssutoday.api.sso.dto

data class SsoGenerateResponse(
    val ssoToken: String,
    val callbackUrl: String,
)
