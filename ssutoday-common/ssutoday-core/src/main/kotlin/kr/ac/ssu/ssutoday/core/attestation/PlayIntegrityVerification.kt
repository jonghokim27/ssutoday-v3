package kr.ac.ssu.ssutoday.core.attestation

/** Google 응답을 해석하지 못한 실패에는 signals가 없다. */
data class PlayIntegrityVerification(
    val verdict: AttestationVerdict,
    val signals: PlayIntegritySignals? = null,
)
