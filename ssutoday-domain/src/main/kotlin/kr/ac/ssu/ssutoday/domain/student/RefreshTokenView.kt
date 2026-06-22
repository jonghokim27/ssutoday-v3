package kr.ac.ssu.ssutoday.domain.student

data class RefreshTokenView(
    val refreshToken: String,
    val accessToken: String,
    val studentId: Int,
    val name: String,
    val major: String,
)
