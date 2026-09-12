package kr.ac.ssu.ssutoday.api.attest.dto

import jakarta.validation.constraints.Positive
import kr.ac.ssu.ssutoday.core.attestation.AttestationPurpose

data class AttestChallengeRequest(
    val purpose: AttestationPurpose,
    @field:Positive val reservationId: Long? = null,
)
