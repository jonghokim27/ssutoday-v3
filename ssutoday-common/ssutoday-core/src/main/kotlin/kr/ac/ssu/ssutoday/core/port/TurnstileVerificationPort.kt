package kr.ac.ssu.ssutoday.core.port

import kr.ac.ssu.ssutoday.core.turnstile.TurnstileAction
import kr.ac.ssu.ssutoday.core.turnstile.TurnstileVerification

interface TurnstileVerificationPort {
    /** 토큰이 유효하고 hostname과 action이 일치하면 검증 결과를, 아니면 null을 반환한다. */
    fun verify(
        token: String,
        action: TurnstileAction,
    ): TurnstileVerification?
}
