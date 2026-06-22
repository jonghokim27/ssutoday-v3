package kr.ac.ssu.ssutoday.domain.sso

data class SsoTokenView(
    val token: String,
    val clientId: String,
    val studentId: Int,
    val name: String,
    val major: String,
)
