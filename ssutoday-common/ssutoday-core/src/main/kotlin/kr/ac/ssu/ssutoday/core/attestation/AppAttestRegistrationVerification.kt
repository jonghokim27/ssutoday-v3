package kr.ac.ssu.ssutoday.core.attestation

data class AppAttestRegistrationVerification(
    val verdict: AttestationVerdict,
    val publicKey: ByteArray? = null,
)
