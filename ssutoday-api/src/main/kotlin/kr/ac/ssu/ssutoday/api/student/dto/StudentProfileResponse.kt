package kr.ac.ssu.ssutoday.api.student.dto

data class StudentProfileResponse(
    val studentId: Int,
    val name: String,
    val major: String,
    val isAdmin: Boolean,
)
