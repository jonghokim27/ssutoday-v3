package kr.ac.ssu.ssutoday.application.student.dto

import kr.ac.ssu.ssutoday.domain.student.StudentView

data class ValidationResult(
    val student: StudentView,
    val accessToken: String? = null,
    val refreshToken: String? = null,
)
