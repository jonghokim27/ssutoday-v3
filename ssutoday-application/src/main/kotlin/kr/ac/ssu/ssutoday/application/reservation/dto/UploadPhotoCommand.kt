package kr.ac.ssu.ssutoday.application.reservation.dto

import java.io.InputStream

data class UploadPhotoCommand(
    val recaptchaToken: String,
    val studentId: Int,
    val reservationId: Long,
    val contentType: String?,
    val size: Long,
    val input: InputStream,
)
