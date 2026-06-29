package kr.ac.ssu.ssutoday.adapter.discord

import kr.ac.ssu.ssutoday.core.port.DiscordVerifyPhotoNotificationPort
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
class DiscordVerifyPhotoNotificationAdapter(
    @Value("\${spring.discord.verify-photo-webhook-url:}") private val webhookUrl: String,
    @Value("\${ssutoday.admin.base-url:}") private val adminBaseUrl: String,
) : DiscordVerifyPhotoNotificationPort {
    private val httpClient: HttpClient =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build()

    override fun send(
        content: String,
        reservationId: Long,
        adminToken: String,
        studentInfo: String,
        roomName: String,
        reservationDateTime: String,
        photoUrl: String,
    ) {
        if (webhookUrl.isBlank()) return

        val photoDeleteUrl = buildAdminActionUrl(adminToken, PHOTO_DELETE)
        val cancelUrl = buildAdminActionUrl(adminToken, RESERVE_CANCEL)
        val payload =
            buildPayload(
                content = content,
                reservationId = reservationId,
                studentInfo = studentInfo,
                roomName = roomName,
                reservationDateTime = reservationDateTime,
                photoUrl = photoUrl,
                photoDeleteUrl = photoDeleteUrl,
                cancelUrl = cancelUrl,
            )

        try {
            val request =
                HttpRequest
                    .newBuilder()
                    .uri(URI.create(buildWebhookUrl(photoDeleteUrl, cancelUrl)))
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
        photoUrl: String,
        photoDeleteUrl: String?,
        cancelUrl: String?,
    ): String {
        val safeContent = escapeJson(content)
        val safePhotoUrl = escapeJson(photoUrl)
        val safeReservationId = escapeJson(reservationId.toString())
        val safeStudentInfo = escapeJson(studentInfo)
        val safeRoomName = escapeJson(roomName)
        val safeReservationDateTime = escapeJson(reservationDateTime)
        val components = buildComponents(photoDeleteUrl, cancelUrl)

        return """
            {
              "content": "$safeContent",
              "embeds": [{
                "image": { "url": "$safePhotoUrl" },
                "fields": [
                  { "name": "예약 고유번호", "value": "$safeReservationId" },
                  { "name": "예약자", "value": "$safeStudentInfo" },
                  { "name": "시설명", "value": "$safeRoomName" },
                  { "name": "예약 날짜 및 시간", "value": "$safeReservationDateTime" }
                ]
              }]$components
            }
            """.trimIndent()
    }

    private fun buildWebhookUrl(
        photoDeleteUrl: String?,
        cancelUrl: String?,
    ): String {
        if (photoDeleteUrl == null && cancelUrl == null) {
            return webhookUrl
        }
        if (webhookUrl.contains("with_components=")) {
            return webhookUrl
        }

        val separator = if (webhookUrl.contains("?")) "&" else "?"
        return "${webhookUrl}${separator}with_components=true"
    }

    private fun buildAdminActionUrl(
        adminToken: String,
        action: String,
    ): String? {
        if (adminBaseUrl.isBlank()) return null

        val encodedToken = URLEncoder.encode(adminToken, StandardCharsets.UTF_8)
        val encodedAction = URLEncoder.encode(action, StandardCharsets.UTF_8)
        return "${adminBaseUrl.trimEnd('/')}/admin/action?token=$encodedToken&action=$encodedAction"
    }

    private fun buildComponents(
        photoDeleteUrl: String?,
        cancelUrl: String?,
    ): String {
        val buttons =
            listOfNotNull(
                photoDeleteUrl?.let {
                    """{ "type": 2, "style": 5, "label": "인증샷 삭제", "url": "${escapeJson(it)}" }"""
                },
                cancelUrl?.let {
                    """{ "type": 2, "style": 5, "label": "예약 취소", "url": "${escapeJson(it)}" }"""
                },
            )
        if (buttons.isEmpty()) return ""

        return """,
          "components": [{
            "type": 1,
            "components": [${buttons.joinToString(", ")}]
          }]"""
    }

    private fun escapeJson(value: String): String =
        value
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
