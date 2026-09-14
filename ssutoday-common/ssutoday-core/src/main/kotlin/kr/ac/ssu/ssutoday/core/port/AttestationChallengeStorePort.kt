package kr.ac.ssu.ssutoday.core.port

import kr.ac.ssu.ssutoday.core.attestation.AttestationChallengeScope
import java.time.Duration

interface AttestationChallengeStorePort {
    /** 기존 challenge를 덮어쓰거나 만료를 연장하지 않는다. */
    fun create(
        challenge: String,
        scope: AttestationChallengeScope,
        ttl: Duration,
    ): Boolean

    /** 만료 전 scope가 일치할 때만 원자적으로 삭제한다. 불일치는 원본을 유지한다. */
    fun consume(
        challenge: String,
        scope: AttestationChallengeScope,
    ): Boolean
}
