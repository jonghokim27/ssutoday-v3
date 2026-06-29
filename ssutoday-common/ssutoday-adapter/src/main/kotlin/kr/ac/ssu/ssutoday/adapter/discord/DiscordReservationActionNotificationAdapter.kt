package kr.ac.ssu.ssutoday.adapter.discord

import kr.ac.ssu.ssutoday.core.port.DiscordReservationActionNotificationPort
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@Component
class DiscordReservationActionNotificationAdapter(
    @Value("\${spring.discord.verify-photo-webhook-url:}") private val webhookUrl: String,
) : DiscordReservationActionNotificationPort {
    private val httpClient: HttpClient =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build()

    override fun send(
        content: String,
        reservationId: Long,
        studentInfo: String,
        roomName: String,
        reservationDateTime: String,
        actionFieldName: String,
        actionFieldValue: String,
        photoUrl: String?,
    ) {
        if (webhookUrl.isBlank()) return

        val payload =
            buildPayload(
                content = content,
                reservationId = reservationId,
                studentInfo = studentInfo,
                roomName = roomName,
                reservationDateTime = reservationDateTime,
                actionFieldName = actionFieldName,
                actionFieldValue = actionFieldValue,
                photoUrl = photoUrl,
            )

        try {
            val request =
                HttpRequest
                    .newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .timeout(Duration.ofSeconds(10))
                    .build()
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
        } catch (_: Exception) {
            // 알림 실패는 무시 (로그는 Logback이 처리)
        }
    }

    private fun buildPayload(
        content: String,
        reservationId: Long,
        studentInfo: String,
        roomName: String,
        reservationDateTime: String,
        actionFieldName: String,
        actionFieldValue: String,
        photoUrl: String?,
    ): String {
        val safeContent = escapeJson(content)
        val safeReservationId = escapeJson(reservationId.toString())
        val safeStudentInfo = escapeJson(studentInfo)
        val safeRoomName = escapeJson(roomName)
        val safeReservationDateTime = escapeJson(reservationDateTime)
        val safeActionFieldName = escapeJson(actionFieldName)
        val safeActionFieldValue = escapeJson(actionFieldValue)
        val image = photoUrl?.let { ""","image": { "url": "${escapeJson(it)}" }""" } ?: ""

        return """
            {
              "content": "$safeContent",
              "embeds": [{
                "fields": [
                  { "name": "예약 고유번호", "value": "$safeReservationId" },
                  { "name": "예약자", "value": "$safeStudentInfo" },
                  { "name": "시설명", "value": "$safeRoomName" },
                  { "name": "예약 날짜 및 시간", "value": "$safeReservationDateTime" },
                  { "name": "$safeActionFieldName", "value": "$safeActionFieldValue" }
                ]$image
              }]
            }
            """.trimIndent()
    }

    private fun escapeJson(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
}
