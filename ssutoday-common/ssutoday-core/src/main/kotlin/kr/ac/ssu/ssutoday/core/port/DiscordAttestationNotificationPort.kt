package kr.ac.ssu.ssutoday.core.port

import kr.ac.ssu.ssutoday.core.attestation.AttestationVerdict

interface DiscordAttestationNotificationPort {
    fun sendReservationResult(
        studentId: Int,
        requestId: Long,
        verdict: AttestationVerdict,
        enforced: Boolean,
    )
}
