package kr.ac.ssu.ssutoday.api.reservation.dto

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import org.springframework.web.multipart.MultipartFile

data class VerifyPhotoRequest(
    @field:NotEmpty
    val recaptchaToken: String,
    @field:NotNull val idx: Long,
    @field:NotNull val file: MultipartFile,
)
