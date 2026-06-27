package kr.ac.ssu.ssutoday.adapter.turnstile

import kr.ac.ssu.ssutoday.core.port.TurnstileVerificationPort
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient

@Component
class TurnstileVerificationAdapter(
    restClientBuilder: RestClient.Builder,
    @Value("\${ssutoday.turnstile.secret-key:}")
    private val secretKey: String,
) : TurnstileVerificationPort {
    private val restClient =
        restClientBuilder
            .baseUrl(VERIFY_URL)
            .build()

    override fun verify(token: String): Boolean {
        if (secretKey.isBlank()) return false

        val form =
            LinkedMultiValueMap<String, String>().apply {
                add("secret", secretKey)
                add("response", token)
            }
        val result =
            restClient
                .post()
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TurnstileVerificationResponse::class.java)
                ?: return false

        return result.success
    }

    private companion object {
        const val VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify"
    }
}
