package kr.ac.ssu.ssutoday.adapter.turnstile

import kr.ac.ssu.ssutoday.core.port.TurnstileVerificationPort
import kr.ac.ssu.ssutoday.core.turnstile.TurnstileAction
import kr.ac.ssu.ssutoday.core.turnstile.TurnstileVerification
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import tools.jackson.databind.JsonNode
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

@Component
class TurnstileVerificationAdapter(
    restClientBuilder: RestClient.Builder,
    @Value("\${ssutoday.turnstile.secret-key:}")
    private val secretKey: String,
    @Value("\${ssutoday.turnstile.hostnames:v3.ssu.today}")
    hostnames: String,
) : TurnstileVerificationPort {
    private val restClient =
        restClientBuilder
            .baseUrl(VERIFY_URL)
            .build()

    private val hostnames =
        hostnames
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()

    init {
        // 빈 허용 목록은 모든 요청을 거부하므로 조용히 동작하지 않고 기동 시 드러낸다.
        require(this.hostnames.isNotEmpty()) { "Turnstile hostname 허용 목록이 비어 있습니다" }
    }

    override fun verify(
        token: String,
        action: TurnstileAction,
    ): TurnstileVerification? {
        if (secretKey.isBlank()) return null

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
                .body(JsonNode::class.java)
                ?: return null

        if (!result.path("success").asBoolean(false)) return null
        if (result.text("hostname") !in hostnames) return null
        if (result.text("action") != action.value) return null
        val challengeTs = result.text("challenge_ts")?.let(::parseInstant) ?: return null
        return TurnstileVerification(challengeTs)
    }

    private fun parseInstant(value: String): Instant? =
        try {
            OffsetDateTime.parse(value).toInstant()
        } catch (_: DateTimeParseException) {
            null
        }

    private fun JsonNode.text(field: String): String? = path(field).takeIf { it.isString }?.asString()

    private companion object {
        const val VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify"
    }
}
