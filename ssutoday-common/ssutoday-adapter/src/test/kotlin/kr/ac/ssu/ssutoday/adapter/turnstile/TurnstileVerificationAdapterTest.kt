package kr.ac.ssu.ssutoday.adapter.turnstile

import kr.ac.ssu.ssutoday.core.turnstile.TurnstileAction
import kr.ac.ssu.ssutoday.core.turnstile.TurnstileVerification
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TurnstileVerificationAdapterTest {
    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val adapter = TurnstileVerificationAdapter(builder, "secret", " v3.ssu.today , localhost ")

    @Test
    fun `hostname과 action이 일치하면 챌린지 통과 시각을 반환한다`() {
        server
            .expect(requestTo(VERIFY_URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().formDataContains(mapOf("secret" to "secret", "response" to "token")))
            .andRespond(respond())

        assertEquals(
            TurnstileVerification(Instant.parse("2026-10-18T11:00:00.096Z")),
            adapter.verify("token", TurnstileAction.RESERVATION_REQUEST),
        )
        server.verify()
    }

    @Test
    fun `허용 목록의 다른 hostname과 offset이 있는 시각도 받는다`() {
        server
            .expect(requestTo(VERIFY_URL))
            .andRespond(respond(hostname = "localhost", action = "verify_photo_upload", challengeTs = "2026-10-18T20:00:00+09:00"))

        assertEquals(
            TurnstileVerification(Instant.parse("2026-10-18T11:00:00Z")),
            adapter.verify("token", TurnstileAction.VERIFY_PHOTO_UPLOAD),
        )
        server.verify()
    }

    @Test
    fun `실패 응답과 다른 hostname action 및 시각 누락은 거부한다`() {
        listOf(
            """{"success":false}""",
            respondBody(success = false),
            respondBody(hostname = "evil.example"),
            respondBody(hostname = null),
            respondBody(action = "verify_photo_upload"),
            respondBody(action = "default"),
            respondBody(action = null),
            respondBody(challengeTs = null),
            respondBody(challengeTs = "not-a-time"),
        ).forEach { body ->
            server.reset()
            server.expect(requestTo(VERIFY_URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON))
            assertNull(adapter.verify("token", TurnstileAction.RESERVATION_REQUEST), body)
            server.verify()
        }
    }

    @Test
    fun `secret이 없으면 Cloudflare를 호출하지 않고 거부한다`() {
        assertNull(TurnstileVerificationAdapter(builder, "", "v3.ssu.today").verify("token", TurnstileAction.RESERVATION_REQUEST))
        server.verify()
    }

    @Test
    fun `빈 hostname 허용 목록은 기동 시 거부한다`() {
        listOf("", " , ").forEach {
            assertFailsWith<IllegalArgumentException> { TurnstileVerificationAdapter(RestClient.builder(), "secret", it) }
        }
    }

    private fun respond(
        hostname: String? = "v3.ssu.today",
        action: String? = "reservation_request",
        challengeTs: String? = "2026-10-18T11:00:00.096Z",
    ) = withSuccess(respondBody(true, hostname, action, challengeTs), MediaType.APPLICATION_JSON)

    private fun respondBody(
        success: Boolean = true,
        hostname: String? = "v3.ssu.today",
        action: String? = "reservation_request",
        challengeTs: String? = "2026-10-18T11:00:00.096Z",
    ): String =
        listOfNotNull(
            "\"success\":$success",
            hostname?.let { "\"hostname\":\"$it\"" },
            action?.let { "\"action\":\"$it\"" },
            challengeTs?.let { "\"challenge_ts\":\"$it\"" },
        ).joinToString(",", "{", "}")

    private companion object {
        const val VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify"
    }
}
