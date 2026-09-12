package kr.ac.ssu.ssutoday.application.attest.dto

import kr.ac.ssu.ssutoday.core.attestation.AttestationPurpose

data class AttestChallengeResult(
    val challenge: String,
    val expiresInSeconds: Long,
    val studentId: Int,
    val purpose: AttestationPurpose,
    val reservationId: Long?,
)
