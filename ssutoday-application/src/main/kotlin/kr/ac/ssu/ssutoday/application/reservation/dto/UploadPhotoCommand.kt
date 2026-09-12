package kr.ac.ssu.ssutoday.application.reservation.dto

import kr.ac.ssu.ssutoday.application.attest.dto.PhotoAttestationEvidence
import java.io.InputStream

data class UploadPhotoCommand(
    val turnstileToken: String,
    val studentId: Int,
    val reservationId: Long,
    val contentType: String?,
    val size: Long,
    val input: InputStream,
    val attestation: PhotoAttestationEvidence = PhotoAttestationEvidence(),
)
