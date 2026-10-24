package kr.ac.ssu.ssutoday.adapter.discord

import kr.ac.ssu.ssutoday.core.port.DiscordPenaltyNotificationPort
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

@Component
class DiscordPenaltyNotificationAdapter(
    @Value("\${spring.discord.verify-photo-webhook-url:}") private val webhookUrl: String,
    @Value("\${ssutoday.admin.base-url:}") private val adminBaseUrl: String,
) : DiscordPenaltyNotificationPort {
    private val httpClient: HttpClient =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build()

    override fun send(
        content: String,
        fields: List<Pair<String, String>>,
        revokeToken: String?,
    ) {
        if (webhookUrl.isBlank()) return

        val revokeUrl = revokeToken?.let(::buildRevokeUrl)
        try {
            val request =
                HttpRequest
                    .newBuilder()
                    .uri(URI.create(buildWebhookUrl(revokeUrl != null)))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(buildPayload(content, fields, revokeUrl)))
                    .timeout(Duration.ofSeconds(10))
                    .build()
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
        } catch (_: Exception) {
            // 알림 실패는 무시 (로그는 Logback이 처리)
        }
    }

    internal fun buildPayload(
        content: String,
        fields: List<Pair<String, String>>,
        revokeUrl: String?,
    ): String {
        val embedFields =
            fields.joinToString(",\n") { (name, value) ->
                """      { "name": "${escapeJson(name)}", "value": "${escapeJson(value)}" }"""
            }
        val components =
            revokeUrl?.let {
                """,
  "components": [{
    "type": 1,
    "components": [{ "type": 2, "style": 5, "label": "페널티 취소", "url": "${escapeJson(it)}" }]
  }]"""
            } ?: ""

        return """{
  "content": "${escapeJson(content)}",
  "embeds": [{
    "color": $EMBED_COLOR,
    "fields": [
$embedFields
    ]
  }]$components
}"""
    }

    private fun buildRevokeUrl(revokeToken: String): String? {
        if (adminBaseUrl.isBlank()) return null

        val encodedToken = URLEncoder.encode(revokeToken, StandardCharsets.UTF_8)
        return "${adminBaseUrl.trimEnd('/')}/admin/action?token=$encodedToken&action=$PENALTY_REVOKE"
    }

    private fun buildWebhookUrl(withComponents: Boolean): String {
        if (!withComponents || webhookUrl.contains("with_components=")) return webhookUrl

        val separator = if (webhookUrl.contains("?")) "&" else "?"
        return "${webhookUrl}${separator}with_components=true"
    }

    private fun escapeJson(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

    private companion object {
        const val PENALTY_REVOKE = "penaltyRevoke"
        const val EMBED_COLOR = 15_875_907
    }
}
