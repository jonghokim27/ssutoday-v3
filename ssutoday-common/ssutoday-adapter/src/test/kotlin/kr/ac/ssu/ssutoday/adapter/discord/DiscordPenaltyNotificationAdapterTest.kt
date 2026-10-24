package kr.ac.ssu.ssutoday.adapter.discord

import tools.jackson.databind.json.JsonMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DiscordPenaltyNotificationAdapterTest {
    private val adapter = DiscordPenaltyNotificationAdapter("", "https://api.ssu.today/")
    private val mapper = JsonMapper.builder().build()

    @Test
    fun `필드 값을 이스케이프하고 취소 버튼을 붙인다`() {
        val payload =
            mapper.readTree(
                adapter.buildPayload(
                    "**[페널티 부여 알림]**",
                    listOf("예약자" to "홍\"길동\" (20260000/컴)", "부여 사유" to "줄바꿈\n포함"),
                    "https://api.ssu.today/admin/action?token=a%2Bb&action=penaltyRevoke",
                ),
            )

        assertEquals("**[페널티 부여 알림]**", payload["content"].asString())
        val fields = payload["embeds"][0]["fields"]
        assertEquals("홍\"길동\" (20260000/컴)", fields[0]["value"].asString())
        assertEquals("줄바꿈\n포함", fields[1]["value"].asString())
        val button = payload["components"][0]["components"][0]
        assertEquals("페널티 취소", button["label"].asString())
        assertEquals("https://api.ssu.today/admin/action?token=a%2Bb&action=penaltyRevoke", button["url"].asString())
    }

    @Test
    fun `취소 링크가 없으면 버튼 없이 보낸다`() {
        val payload = mapper.readTree(adapter.buildPayload("**[페널티 취소 알림]**", listOf("취소 사유" to "이의제기 인용"), null))

        assertFalse(payload.has("components"))
        assertEquals("이의제기 인용", payload["embeds"][0]["fields"][0]["value"].asString())
    }
}
