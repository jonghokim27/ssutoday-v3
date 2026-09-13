package kr.ac.ssu.ssutoday.application.attest.dto

data class RegisterAppAttestCommand(
    val studentId: Int,
    val keyId: String,
    val challenge: String,
    val attestation: String,
)
