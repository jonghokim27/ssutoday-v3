package kr.ac.ssu.ssutoday.core.turnstile

import java.time.Instant

/** Cloudflare가 확인한 토큰 정보. challengeTs는 사용자가 챌린지를 통과한 시각이다. */
data class TurnstileVerification(
    val challengeTs: Instant,
)
