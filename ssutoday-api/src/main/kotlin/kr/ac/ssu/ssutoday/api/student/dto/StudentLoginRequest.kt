package kr.ac.ssu.ssutoday.api.student.dto

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

data class StudentLoginRequest(
    @field:NotEmpty val sToken: String,
    @field:NotNull val sIdno: Int,
    val persistLogin: Boolean = false,
)
