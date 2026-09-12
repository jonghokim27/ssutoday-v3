package kr.ac.ssu.ssutoday.api.attest.dto

import kr.ac.ssu.ssutoday.core.attestation.AttestationPurpose

data class AttestChallengeResponse(
    val challenge: String,
    val expiresInSeconds: Long,
    val studentId: Int,
    val purpose: AttestationPurpose,
    val reservationId: Long?,
)
