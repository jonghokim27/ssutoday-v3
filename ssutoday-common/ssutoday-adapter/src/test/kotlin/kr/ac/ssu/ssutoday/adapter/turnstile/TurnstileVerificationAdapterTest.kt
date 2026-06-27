package kr.ac.ssu.ssutoday.adapter.turnstile

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



class TurnstileVerificationAdapterTest {
    @Test
    fun `success가 true이면 검증에 성공한다`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val adapter = TurnstileVerificationAdapter(builder, secretKey = "secret")
        server
            .expect(requestTo("https://challenges.cloudflare.com/turnstile/v0/siteverify"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().formDataContains(mapOf("secret" to "secret", "response" to "token")))
            .andRespond(withSuccess("""{"success":true}""", MediaType.APPLICATION_JSON))

        assertTrue(adapter.verify("token"))
        server.verify()
    }

    @Test
    fun `success가 false이면 검증에 실패한다`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val adapter = TurnstileVerificationAdapter(builder, secretKey = "secret")
        server
            .expect(requestTo("https://challenges.cloudflare.com/turnstile/v0/siteverify"))
            .andRespond(withSuccess("""{"success":false}""", MediaType.APPLICATION_JSON))

        assertFalse(adapter.verify("token"))
        server.verify()
    }
}
