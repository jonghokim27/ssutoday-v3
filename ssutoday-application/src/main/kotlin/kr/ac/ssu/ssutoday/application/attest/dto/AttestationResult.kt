package kr.ac.ssu.ssutoday.application.attest.dto

import kr.ac.ssu.ssutoday.core.attestation.AttestationVerdict
import kr.ac.ssu.ssutoday.core.attestation.PlayIntegritySignals

data class AttestationResult(
    val verdict: AttestationVerdict,
    val platform: String,
    val enforced: Boolean,
    /** Android에서 Google 응답을 해석한 경우에만 있다. 판정에는 쓰지 않는다. */
    val signals: PlayIntegritySignals? = null,
)
