package kr.ac.ssu.ssutoday.api.student.dto

import jakarta.validation.constraints.NotEmpty

data class StudentUpdateTokenRequest(
    @field:NotEmpty val xnApiToken: String,
)
