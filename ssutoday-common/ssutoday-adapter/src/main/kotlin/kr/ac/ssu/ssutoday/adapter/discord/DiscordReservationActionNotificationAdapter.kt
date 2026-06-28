package kr.ac.ssu.ssutoday.adapter.discord

import kr.ac.ssu.ssutoday.core.port.DiscordReservationActionNotificationPort
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDate

@Component
class DiscordReservationActionNotificationAdapter(
    @Value("\${spring.discord.verify-photo-webhook-url:}") private val webhookUrl: String,
) : DiscordReservationActionNotificationPort {
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    override fun send(
        title: String,
        reservationId: Long,
        studentId: Int,
        roomNo: String,
        date: LocalDate,
        startBlock: Int,
        endBlock: Int,
        reason: String,
        photoUrl: String?,
    ) {
        if (webhookUrl.isBlank()) return

        val startTime = blockToTime(startBlock)
        val endTime = blockToTime(endBlock + 1)

        val payload = buildPayload(
            title = title,
            reservationId = reservationId,
            studentId = studentId,
            roomNo = roomNo,
            date = date.toString(),
            time = "$startTime ~ $endTime",
            reason = reason,
            photoUrl = photoUrl,
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
        title: String,
        reservationId: Long,
        studentId: Int,
        roomNo: String,
        date: String,
        time: String,
        reason: String,
        photoUrl: String?,
    ): String {
        val description = escapeJson(
            "**예약 ID:** $reservationId\n" +
                "**학번:** $studentId\n" +
                "**강의실:** $roomNo\n" +
                "**날짜:** $date\n" +
                "**시간:** $time\n" +
                "**사유:** $reason"
        )
        val safeTitle = escapeJson(title)
        val image = photoUrl?.let { ""","image": { "url": "${escapeJson(it)}" }""" } ?: ""

        return """
        {
          "embeds": [{
            "title": "$safeTitle",
            "description": "$description",
            "color": 15158332
            $image
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
