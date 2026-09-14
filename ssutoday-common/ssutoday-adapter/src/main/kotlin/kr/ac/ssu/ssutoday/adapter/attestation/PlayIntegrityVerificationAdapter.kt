package kr.ac.ssu.ssutoday.adapter.attestation

import kr.ac.ssu.ssutoday.core.attestation.AttestationVerdict
import kr.ac.ssu.ssutoday.core.port.PlayIntegrityVerificationPort
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import tools.jackson.core.JacksonException
import tools.jackson.databind.ObjectMapper
import java.io.IOException
import java.time.Instant

@Component
class PlayIntegrityVerificationAdapter(
    @Qualifier("playIntegrityRestClient") private val restClient: RestClient,
    private val tokenProvider: PlayIntegrityAccessTokenProvider,
    private val objectMapper: ObjectMapper,
    @Value("\${ssutoday.attestation.play-integrity.package-name}")
    private val packageName: String,
    @Value("\${ssutoday.attestation.play-integrity.certificate-sha256}")
    certificateSha256: String,
) : PlayIntegrityVerificationPort {
    private val verifier = PlayIntegrityVerdictVerifier(packageName, certificateSha256)

    override fun verify(
        token: String,
        requestHash: String,
    ): AttestationVerdict {
        return try {
            val accessToken = tokenProvider.getAccessToken() ?: return AttestationVerdict.NOT_CONFIGURED
            val response =
                restClient
                    .post()
                    .uri("/v1/{packageName}:decodeIntegrityToken", packageName)
                    .headers { it.setBearerAuth(accessToken) }
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(mapOf("integrity_token" to token))
                    .retrieve()
                    .body(String::class.java)
                    ?: return AttestationVerdict.INVALID_RESPONSE
            val payload = objectMapper.readTree(response) ?: return AttestationVerdict.INVALID_RESPONSE
            verifier.verify(payload, requestHash, Instant.now())
        } catch (exception: RestClientResponseException) {
            when (exception.statusCode.value()) {
                400 -> AttestationVerdict.TOKEN_REJECTED
                401, 403 -> AttestationVerdict.PROVIDER_AUTHORIZATION_FAILED
                else -> AttestationVerdict.PROVIDER_UNAVAILABLE
            }
        } catch (_: RestClientException) {
            AttestationVerdict.PROVIDER_UNAVAILABLE
        } catch (_: IOException) {
            AttestationVerdict.PROVIDER_UNAVAILABLE
        } catch (_: JacksonException) {
            AttestationVerdict.INVALID_RESPONSE
        }
        // 외부 예외의 메시지/응답 본문에는 토큰이 포함될 수 있으므로 전파하거나 기록하지 않는다.
    }
}
