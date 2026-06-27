package kr.ac.ssu.ssutoday.core.port

interface TurnstileVerificationPort {
    fun verify(token: String): Boolean
}
