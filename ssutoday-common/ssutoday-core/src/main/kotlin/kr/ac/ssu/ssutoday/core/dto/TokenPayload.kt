package kr.ac.ssu.ssutoday.core.dto

data class TokenPayload(
    val studentId: Int,
    val name: String,
    val major: String,
)
