package kr.ac.ssu.ssutoday.adapter.recaptcha

import kr.ac.ssu.ssutoday.core.port.RecaptchaVerificationPort
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient

@Component
class RecaptchaVerificationAdapter(
    restClientBuilder: RestClient.Builder,
    @Value("\${ssutoday.recaptcha.secret-key:}")
    private val secretKey: String,
    @Value("\${ssutoday.recaptcha.minimum-score:0.5}")
    private val minimumScore: Double,
    @Value("\${ssutoday.recaptcha.allowed-hostnames:}")
    allowedHostnames: String,
) : RecaptchaVerificationPort {
    private val restClient = restClientBuilder
        .baseUrl(VERIFY_URL)
        .build()

    private val allowedHostnames = allowedHostnames
        .split(",")
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toSet()

    override fun verify(token: String, expectedAction: String): Boolean {
        if (secretKey.isBlank()) return false

        val form = LinkedMultiValueMap<String, String>().apply {
            add("secret", secretKey)
            add("response", token)
        }
        val result = restClient.post()
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(RecaptchaVerificationResponse::class.java)
            ?: return false

        val score = result.score ?: return false
        return result.success &&
            result.action == expectedAction &&
            score >= minimumScore &&
            (allowedHostnames.isEmpty() || result.hostname in allowedHostnames)
    }

    private companion object {
        const val VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify"
    }
}
