package kr.ac.ssu.ssutoday.application.student.dto

data class LoginResult(
    val accessToken: String,
    val refreshToken: String,
    val studentId: Int,
    val name: String,
    val major: String,
    val isAdmin: Boolean,
)
