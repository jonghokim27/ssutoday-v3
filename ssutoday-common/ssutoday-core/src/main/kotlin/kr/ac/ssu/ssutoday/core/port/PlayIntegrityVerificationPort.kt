package kr.ac.ssu.ssutoday.core.port

import kr.ac.ssu.ssutoday.core.attestation.AttestationVerdict

interface PlayIntegrityVerificationPort {
    /** Standard 요청의 Google 판정과 서버에서 재구성한 requestHash를 검증한다. */
    fun verify(
        token: String,
        requestHash: String,
    ): AttestationVerdict
}
