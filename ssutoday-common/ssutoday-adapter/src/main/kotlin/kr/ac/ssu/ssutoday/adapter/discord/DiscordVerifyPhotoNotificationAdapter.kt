package kr.ac.ssu.ssutoday.adapter.discord

import kr.ac.ssu.ssutoday.core.port.DiscordVerifyPhotoNotificationPort
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDate

@Component
class DiscordVerifyPhotoNotificationAdapter(
    @Value("\${spring.discord.verify-photo-webhook-url:}") private val webhookUrl: String,
    @Value("\${ssutoday.admin.base-url:}") private val adminBaseUrl: String,
) : DiscordVerifyPhotoNotificationPort {
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    override fun send(
        reservationId: Long,
        adminToken: String,
        studentId: Int,
        roomNo: String,
        date: LocalDate,
        startBlock: Int,
        endBlock: Int,
        photoUrl: String,
    ) {
        if (webhookUrl.isBlank()) return

        val startTime = blockToTime(startBlock)
        val endTime = blockToTime(endBlock + 1)
        val photoDeleteUrl = "$adminBaseUrl/admin/action?token=${adminToken}&action=photoDelete"
        val cancelUrl = "$adminBaseUrl/admin/action?token=${adminToken}&action=reserveCancel"

        val payload = buildPayload(
            reservationId = reservationId,
            studentId = studentId,
            roomNo = roomNo,
            date = date.toString(),
            time = "$startTime ~ $endTime",
            photoUrl = photoUrl,
            photoDeleteUrl = photoDeleteUrl,
            cancelUrl = cancelUrl,
        )

        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .timeout(Duration.ofSeconds(10))
                .build()
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
        } catch (e: Exception) {
            // 알림 실패는 무시 (로그는 Logback이 처리)
        }
    }

    private fun blockToTime(block: Int): String {
        val totalMinutes = block * 30
        return "%02d:%02d".format(totalMinutes / 60, totalMinutes % 60)
    }

    private fun buildPayload(
        reservationId: Long,
        studentId: Int,
        roomNo: String,
        date: String,
        time: String,
        photoUrl: String,
        photoDeleteUrl: String,
        cancelUrl: String,
    ): String {
        val description = escapeJson(
            "**예약 ID:** $reservationId\n" +
            "**학번:** $studentId\n" +
            "**강의실:** $roomNo\n" +
            "**날짜:** $date\n" +
            "**시간:** $time"
        )
        val safePhotoUrl = escapeJson(photoUrl)
        val safePhotoDeleteUrl = escapeJson(photoDeleteUrl)
        val safeCancelUrl = escapeJson(cancelUrl)

        return """
        {
          "embeds": [{
            "title": "인증샷 등록",
            "description": "$description",
            "color": 5763719,
            "image": { "url": "$safePhotoUrl" }
          }],
          "components": [{
            "type": 1,
            "components": [
              {
                "type": 2,
                "style": 5,
                "label": "인증샷 삭제",
                "emoji": { "name": "🗑️" },
                "url": "$safePhotoDeleteUrl"
              },
              {
                "type": 2,
                "style": 5,
                "label": "예약 취소",
                "emoji": { "name": "❌" },
                "url": "$safeCancelUrl"
              }
            ]
          }]
        }
        """.trimIndent()
    }

    private fun escapeJson(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}
