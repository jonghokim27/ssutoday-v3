package kr.ac.ssu.ssutoday.api.sso.dto

data class SsoValidateResponse(
    val studentId: Int,
    val name: String,
    val major: String,
)
