package kr.ac.ssu.ssutoday.adapter.recaptcha

data class RecaptchaVerificationResponse(
    var success: Boolean = false,
    var score: Double? = null,
    var action: String? = null,
    var hostname: String? = null,
)
