package kr.ac.ssu.ssutoday.adapter.attestation

import kr.ac.ssu.ssutoday.core.attestation.AttestationVerdict
import kr.ac.ssu.ssutoday.core.port.PlayIntegrityVerificationPort
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayIntegrityConfigurationTest {
    @Test
    fun `키 없이 빈을 구성할 수 있고 공용 RestClient builder를 변경하지 않는다`() {
        val builder = RestClient.builder().baseUrl("https://unchanged.invalid")
        val server = MockRestServiceServer.bindTo(builder).build()
        ApplicationContextRunner()
            .withUserConfiguration(
                PlayIntegrityHttpConfig::class.java,
                PlayIntegrityAccessTokenProvider::class.java,
                PlayIntegrityVerificationAdapter::class.java,
            ).withBean(RestClient.Builder::class.java, { builder })
            .withBean(ObjectMapper::class.java, { JsonMapper.builder().build() })
            .withPropertyValues(
                "ssutoday.attestation.play-integrity.package-name=com.ssutoday",
                "ssutoday.attestation.play-integrity.certificate-sha256=${"00".repeat(32)}",
                "ssutoday.attestation.play-integrity.credentials=",
            ).run { context ->
                assertNull(context.startupFailure)
                assertEquals(
                    AttestationVerdict.NOT_CONFIGURED,
                    context.getBean(PlayIntegrityVerificationPort::class.java).verify("token", "hash"),
                )
                server.expect(requestTo("https://unchanged.invalid/ping")).andRespond(withSuccess())
                builder
                    .build()
                    .get()
                    .uri("/ping")
                    .retrieve()
                    .toBodilessEntity()
                server.verify()
            }
    }
}
