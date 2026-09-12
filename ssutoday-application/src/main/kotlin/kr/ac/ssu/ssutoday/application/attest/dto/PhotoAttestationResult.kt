package kr.ac.ssu.ssutoday.application.attest.dto

import kr.ac.ssu.ssutoday.core.attestation.AttestationVerdict

data class PhotoAttestationResult(
    val verdict: AttestationVerdict,
    val platform: String,
    val enforced: Boolean,
)
