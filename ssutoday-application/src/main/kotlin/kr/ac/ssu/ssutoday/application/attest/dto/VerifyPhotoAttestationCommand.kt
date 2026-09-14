package kr.ac.ssu.ssutoday.application.attest.dto

data class VerifyPhotoAttestationCommand(
    val studentId: Int,
    val reservationId: Long,
    val photo: ByteArray,
    val evidence: AttestationEvidence,
)
