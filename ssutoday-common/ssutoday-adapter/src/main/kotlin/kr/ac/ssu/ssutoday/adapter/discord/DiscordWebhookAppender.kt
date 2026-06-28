package kr.ac.ssu.ssutoday.adapter.discord

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.IThrowableProxy
import ch.qos.logback.core.AppenderBase
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class DiscordWebhookAppender : AppenderBase<ILoggingEvent>() {
    var webhookUrl: String = ""
    var appName: String = "ssutoday"

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    override fun append(event: ILoggingEvent) {
        if (webhookUrl.isBlank()) return

        val color = when (event.level) {
            Level.ERROR -> 15158332  // Red
            else -> 16776960         // Yellow (WARN)
        }

        val stackTrace = buildStackTrace(event.throwableProxy)
        val description = buildDescription(event, stackTrace)
        val title = "[${appName}] [${event.level}] ${event.formattedMessage}".take(256)
        val payload = buildJsonPayload(title, description, color)

        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .timeout(Duration.ofSeconds(10))
                .build()
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
        } catch (e: Exception) {
            addError("Discord 웹훅 전송 실패", e)
        }
    }

    private fun buildDescription(event: ILoggingEvent, stackTrace: String): String {
        return buildString {
            append("**Logger:** `${event.loggerName}`\n")
            append("**Message:** ${event.formattedMessage}")
            if (stackTrace.isNotEmpty()) {
                val truncated = if (stackTrace.length > 3400) stackTrace.take(3400) + "\n... (truncated)" else stackTrace
                append("\n\n**Stack Trace:**\n```\n$truncated\n```")
            }
        }.take(4096)
    }

    private fun buildStackTrace(proxy: IThrowableProxy?): String {
        if (proxy == null) return ""
        return buildString {
            append("${proxy.className}: ${proxy.message}\n")
            proxy.stackTraceElementProxyArray?.take(20)?.forEach {
                append("\tat ${it.steAsString}\n")
            }
            var cause = proxy.cause
            var depth = 0
            while (cause != null && depth < 5) {
                append("Caused by: ${cause.className}: ${cause.message}\n")
                cause.stackTraceElementProxyArray?.take(10)?.forEach {
                    append("\tat ${it.steAsString}\n")
                }
                cause = cause.cause
                depth++
            }
        }
    }

    private fun buildJsonPayload(title: String, description: String, color: Int): String {
        val safeTitle = escapeJson(title)
        val safeDesc = escapeJson(description)
        return """{"embeds":[{"title":"$safeTitle","description":"$safeDesc","color":$color}]}"""
    }

    private fun escapeJson(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}
