package kr.ac.ssu.ssutoday.adapter.recaptcha

import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecaptchaVerificationAdapterTest {
    @Test
    fun `action과 score 및 hostname이 일치하면 검증에 성공한다`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val adapter = RecaptchaVerificationAdapter(
            builder,
            secretKey = "secret",
            minimumScore = 0.5,
            allowedHostnames = "ssu.today,www.ssu.today",
        )
        server.expect(requestTo("https://www.google.com/recaptcha/api/siteverify"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().formDataContains(mapOf("secret" to "secret", "response" to "token")))
            .andRespond(
                withSuccess(
                    """
                    {
                      "success": true,
                      "score": 0.9,
                      "action": "reservation_request",
                      "challenge_ts": "2026-06-22T12:00:00Z",
                      "hostname": "ssu.today",
                      "error-codes": []
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertTrue(adapter.verify("token", "reservation_request"))
        server.verify()
    }

    @Test
    fun `action이 다르거나 score가 낮으면 검증에 실패한다`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val adapter = RecaptchaVerificationAdapter(
            builder,
            secretKey = "secret",
            minimumScore = 0.5,
            allowedHostnames = "",
        )
        server.expect(requestTo("https://www.google.com/recaptcha/api/siteverify"))
            .andRespond(
                withSuccess(
                    """{"success":true,"score":0.4,"action":"other","hostname":"ssu.today"}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertFalse(adapter.verify("token", "reservation_request"))
        server.verify()
    }
}
