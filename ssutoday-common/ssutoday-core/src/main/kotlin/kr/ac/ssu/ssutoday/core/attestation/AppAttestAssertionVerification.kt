package kr.ac.ssu.ssutoday.core.attestation

data class AppAttestAssertionVerification(
    val verdict: AttestationVerdict,
    val counter: Long? = null,
)
