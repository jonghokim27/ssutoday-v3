package kr.ac.ssu.ssutoday.core.port

import kr.ac.ssu.ssutoday.core.attestation.AppAttestAssertionVerification
import kr.ac.ssu.ssutoday.core.attestation.AppAttestRegistrationVerification

interface AppAttestVerificationPort {
    fun verifyRegistration(
        attestation: String,
        keyId: String,
        clientDataHash: ByteArray,
    ): AppAttestRegistrationVerification

    fun verifyAssertion(
        assertion: String,
        publicKey: ByteArray,
        clientDataHash: ByteArray,
    ): AppAttestAssertionVerification
}
