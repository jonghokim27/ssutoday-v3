package kr.ac.ssu.ssutoday.core.port

interface RecaptchaVerificationPort {
    fun verify(token: String, expectedAction: String): Boolean
}
