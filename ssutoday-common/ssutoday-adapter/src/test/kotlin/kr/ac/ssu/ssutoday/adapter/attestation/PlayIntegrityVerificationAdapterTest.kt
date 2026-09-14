package kr.ac.ssu.ssutoday.adapter.attestation

import kr.ac.ssu.ssutoday.core.attestation.AttestationVerdict
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper
import java.io.IOException
import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayIntegrityVerificationAdapterTest {
    private val builder = RestClient.builder().baseUrl("https://playintegrity.googleapis.com")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val provider = mock(PlayIntegrityAccessTokenProvider::class.java)
    private val mapper = JsonMapper.builder().build()
    private val adapter = PlayIntegrityVerificationAdapter(builder.build(), provider, mapper, "com.ssutoday", "00".repeat(32))

    @Test
    fun `Google로만 OAuth와 원문 토큰을 전송하고 응답 판정을 검증한다`() {
        val token = "opaque-token-with-\"-and-\\"
        val digest = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32))
        `when`(provider.getAccessToken()).thenReturn("test-access-token")
        server
            .expect(requestTo("https://playintegrity.googleapis.com/v1/com.ssutoday:decodeIntegrityToken"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer test-access-token"))
            .andExpect(content().json(mapper.writeValueAsString(mapOf("integrity_token" to token))))
            .andRespond(
                withSuccess(
                    """
                    {"tokenPayloadExternal": {
                      "requestDetails":{"requestPackageName":"com.ssutoday","requestHash":"expected","timestampMillis":"${Instant.now().toEpochMilli()}"},
                      "appIntegrity":{"packageName":"com.ssutoday","appRecognitionVerdict":"PLAY_RECOGNIZED","certificateSha256Digest":["$digest"]},
                      "deviceIntegrity":{"deviceRecognitionVerdict":["MEETS_DEVICE_INTEGRITY"]}
                    }}
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )
        assertEquals(AttestationVerdict.VERIFIED, adapter.verify(token, "expected"))
        server.verify()
    }

    @Test
    fun `설정 누락과 OAuth 장애는 성공으로 처리하지 않는다`() {
        assertEquals(AttestationVerdict.NOT_CONFIGURED, adapter.verify("token", "hash"))
        `when`(provider.getAccessToken()).thenAnswer { throw IOException("sensitive-token-provider-error") }
        assertEquals(AttestationVerdict.PROVIDER_UNAVAILABLE, adapter.verify("token", "hash"))
        server.verify()
    }

    @Test
    fun `HTTP 오류와 시간 초과는 본문을 노출하지 않고 분류하며 토큰을 재시도하지 않는다`() {
        `when`(provider.getAccessToken()).thenReturn("access")
        val cases =
            mapOf(
                HttpStatus.BAD_REQUEST to AttestationVerdict.TOKEN_REJECTED,
                HttpStatus.UNAUTHORIZED to AttestationVerdict.PROVIDER_AUTHORIZATION_FAILED,
                HttpStatus.FORBIDDEN to AttestationVerdict.PROVIDER_AUTHORIZATION_FAILED,
                HttpStatus.TOO_MANY_REQUESTS to AttestationVerdict.PROVIDER_UNAVAILABLE,
                HttpStatus.INTERNAL_SERVER_ERROR to AttestationVerdict.PROVIDER_UNAVAILABLE,
            )
        cases.forEach { (status, verdict) ->
            server.reset()
            server.expect(requestTo(ENDPOINT)).andRespond(withStatus(status).body("sensitive-token-in-error"))
            assertEquals(verdict, adapter.verify("token", "hash"))
            server.verify()
        }
        server.reset()
        server.expect(requestTo(ENDPOINT)).andRespond(withException(IOException("timeout")))
        assertEquals(AttestationVerdict.PROVIDER_UNAVAILABLE, adapter.verify("token", "hash"))
        server.verify()
    }

    @Test
    fun `빈 응답 잘못된 JSON과 판정 누락은 실패한다`() {
        `when`(provider.getAccessToken()).thenReturn("access")
        listOf("", " ", "not-json", "null", "{}", "[]").forEach { body ->
            server.reset()
            server.expect(requestTo(ENDPOINT)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON))
            assertEquals(AttestationVerdict.INVALID_RESPONSE, adapter.verify("token", "hash"), body)
            server.verify()
        }
    }

    private companion object {
        const val ENDPOINT = "https://playintegrity.googleapis.com/v1/com.ssutoday:decodeIntegrityToken"
    }
}
