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
        studentName: String,
        roomNo: String,
        roomName: String,
        date: LocalDate,
        startBlock: Int,
        endBlock: Int,
        photoUrl: String,
    ) {
        if (webhookUrl.isBlank()) return

        val startTime = blockToTime(startBlock)
        val endTime = blockToTime(endBlock + 1)
        val photoDeleteUrl = buildAdminActionUrl(adminToken, PHOTO_DELETE)
        val cancelUrl = buildAdminActionUrl(adminToken, RESERVE_CANCEL)

        val payload = buildPayload(
            reservationId = reservationId,
            studentId = studentId,
            studentName = studentName,
            roomNo = roomNo,
            roomName = roomName,
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
        studentName: String,
        roomNo: String,
        roomName: String,
        date: String,
        time: String,
        photoUrl: String,
        photoDeleteUrl: String?,
        cancelUrl: String?,
    ): String {
        val description = escapeJson(
            "**예약 고유번호:** $reservationId\n" +
                "**예약자:** $studentName ($studentId)\n" +
                "**시설명:** $roomName ($roomNo)\n" +
                "**예약 날짜 및 시간:** $date $time",
        )
        val safePhotoUrl = escapeJson(photoUrl)
        val components = buildComponents(photoDeleteUrl, cancelUrl)

        return """
        {
          "embeds": [{
            "title": "인증샷 등록 알림",
            "description": "$description",
            "color": 5763719,
            "image": { "url": "$safePhotoUrl" }
          }]$components
        }
        """.trimIndent()
    }

    private fun buildAdminActionUrl(
        adminToken: String,
        action: String,
    ): String? {
        if (adminBaseUrl.isBlank()) return null

        return "${adminBaseUrl.trimEnd('/')}/admin/action?token=$adminToken&action=$action"
    }

    private fun buildComponents(
        photoDeleteUrl: String?,
        cancelUrl: String?,
    ): String {
        val buttons = listOfNotNull(
            photoDeleteUrl?.let {
                """
              {
                "type": 2,
                "style": 5,
                "label": "인증샷 삭제",
                "url": "${escapeJson(it)}"
              }
                """.trimIndent()
            },
            cancelUrl?.let {
                """
              {
                "type": 2,
                "style": 5,
                "label": "예약 취소",
                "url": "${escapeJson(it)}"
              }
                """.trimIndent()
            },
        )
        if (buttons.isEmpty()) return ""

        return """,
          "components": [{
            "type": 1,
            "components": [
              ${buttons.joinToString(",\n              ")}
            ]
          }]"""
    }

    private fun escapeJson(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

    private companion object {
        const val PHOTO_DELETE = "photoDelete"
        const val RESERVE_CANCEL = "reserveCancel"
    }
}
