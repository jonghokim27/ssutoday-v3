package kr.ac.ssu.ssutoday.adapter.discord

import kr.ac.ssu.ssutoday.core.attestation.AttestationVerdict
import kr.ac.ssu.ssutoday.core.port.DiscordAttestationNotificationPort
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@Component
class DiscordAttestationNotificationAdapter(
    @Value("\${spring.discord.verify-photo-webhook-url:}") private val webhookUrl: String,
) : DiscordAttestationNotificationPort {
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    private val mapper = JsonMapper.builder().build()

    override fun sendReservationResult(
        studentId: Int,
        requestId: Long,
        verdict: AttestationVerdict,
        enforced: Boolean,
    ) {
        if (webhookUrl.isBlank()) return
        try {
            val payload =
                mapper.writeValueAsString(
                    mapOf(
                        "content" to
                            (
                                "**[예약 요청 앱 무결성]**\n학생: $studentId / 요청: $requestId\n" +
                                    "판정: $verdict (${if (enforced) "강제 모드" else "관찰 모드"})"
                            ),
                        "allowed_mentions" to mapOf("parse" to emptyList<String>()),
                    ),
                )
            val request =
                HttpRequest
                    .newBuilder(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build()
            http.sendAsync(request, HttpResponse.BodyHandlers.discarding())
        } catch (_: Exception) {
            // 알림 실패가 이미 접수한 예약에 영향을 주지 않으며 webhook/증명 원문을 로그에 남기지 않는다.
        }
    }
}
