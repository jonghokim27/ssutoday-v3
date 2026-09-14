package kr.ac.ssu.ssutoday.adapter.attestation

import kr.ac.ssu.ssutoday.core.attestation.AttestationVerdict
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode
import java.time.Instant
import java.util.Base64
import java.util.HexFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PlayIntegrityVerdictVerifierTest {
    private val mapper = JsonMapper.builder().build()
    private val now = Instant.parse("2026-09-13T00:00:00Z")
    private val certificate = "30:B0:61:28:59:F6:64:57:59:11:18:AC:CD:A3:98:B4:D9:D9:C4:AE:CC:BE:C0:A5:11:40:99:B4:22:CE:2F:ED"
    private val digest = Base64.getUrlEncoder().withoutPadding().encodeToString(HexFormat.of().parseHex(certificate.replace(":", "")))
    private val verifier = PlayIntegrityVerdictVerifier("com.ssutoday", certificate)

    @Test
    fun `Google Standard 판정의 모든 필수 조건을 만족하면 성공한다`() {
        assertEquals(AttestationVerdict.VERIFIED, verifier.verify(response(), REQUEST_HASH, now))
        assertEquals(AttestationVerdict.VERIFIED, verifier.verify(response(digest + "="), REQUEST_HASH, now))
    }

    @Test
    fun `필수 판정의 변조 누락과 잘못된 형식은 거부한다`() {
        val changes =
            listOf(
                Triple("requestDetails", "requestPackageName", "\"com.other\"" to AttestationVerdict.PACKAGE_MISMATCH),
                Triple("requestDetails", "requestHash", "\"changed\"" to AttestationVerdict.REQUEST_HASH_MISMATCH),
                Triple("requestDetails", "requestHash", "null" to AttestationVerdict.REQUEST_HASH_MISMATCH),
                Triple("requestDetails", "timestampMillis", "\"not-a-time\"" to AttestationVerdict.INVALID_RESPONSE),
                Triple("requestDetails", "timestampMillis", "null" to AttestationVerdict.INVALID_RESPONSE),
                Triple("requestDetails", "timestampMillis", "1.5" to AttestationVerdict.INVALID_RESPONSE),
                Triple("appIntegrity", "packageName", "\"com.other\"" to AttestationVerdict.PACKAGE_MISMATCH),
                Triple("appIntegrity", "appRecognitionVerdict", "\"UNRECOGNIZED_VERSION\"" to AttestationVerdict.APP_UNRECOGNIZED),
                Triple("appIntegrity", "appRecognitionVerdict", "\"UNEVALUATED\"" to AttestationVerdict.APP_UNRECOGNIZED),
                Triple("appIntegrity", "certificateSha256Digest", "[]" to AttestationVerdict.CERTIFICATE_MISMATCH),
                Triple("appIntegrity", "certificateSha256Digest", "[\"bad+base64\"]" to AttestationVerdict.CERTIFICATE_MISMATCH),
                Triple("appIntegrity", "certificateSha256Digest", "[\"$digest\",\"AAAA\"]" to AttestationVerdict.CERTIFICATE_MISMATCH),
                Triple("deviceIntegrity", "deviceRecognitionVerdict", "[]" to AttestationVerdict.DEVICE_UNTRUSTED),
                Triple("deviceIntegrity", "deviceRecognitionVerdict", "null" to AttestationVerdict.DEVICE_UNTRUSTED),
                Triple("deviceIntegrity", "deviceRecognitionVerdict", "[\"MEETS_BASIC_INTEGRITY\"]" to AttestationVerdict.DEVICE_UNTRUSTED),
                Triple("deviceIntegrity", "deviceRecognitionVerdict", "\"MEETS_DEVICE_INTEGRITY\"" to AttestationVerdict.DEVICE_UNTRUSTED),
                Triple(
                    "deviceIntegrity",
                    "deviceRecognitionVerdict",
                    "[\"NOT_MEETS_DEVICE_INTEGRITY\"]" to AttestationVerdict.DEVICE_UNTRUSTED,
                ),
            )
        changes.forEach { (section, field, mutation) ->
            val response = response()
            (response.path("tokenPayloadExternal").path(section) as ObjectNode).set(field, mapper.readTree(mutation.first))
            assertEquals(mutation.second, verifier.verify(response, REQUEST_HASH, now), "$section.$field=${mutation.first}")
        }
        listOf("{}", "null", "[]", "{\"tokenPayloadExternal\":{}}").forEach {
            assertEquals(AttestationVerdict.INVALID_RESPONSE, verifier.verify(mapper.readTree(it), REQUEST_HASH, now))
        }
    }

    @Test
    fun `발급 시각의 만료 미래 경계와 극단값을 검증한다`() {
        val cases =
            mapOf(
                now.toEpochMilli() - 120_000 to AttestationVerdict.VERIFIED,
                now.toEpochMilli() - 120_001 to AttestationVerdict.TOKEN_EXPIRED,
                now.toEpochMilli() + 30_000 to AttestationVerdict.VERIFIED,
                now.toEpochMilli() + 30_001 to AttestationVerdict.TOKEN_FROM_FUTURE,
                Long.MIN_VALUE to AttestationVerdict.TOKEN_EXPIRED,
                Long.MAX_VALUE to AttestationVerdict.TOKEN_FROM_FUTURE,
            )
        cases.forEach { (timestamp, verdict) ->
            val response = response()
            (response.path("tokenPayloadExternal").path("requestDetails") as ObjectNode).put("timestampMillis", timestamp.toString())
            assertEquals(verdict, verifier.verify(response, REQUEST_HASH, now))
        }
    }

    @Test
    fun `인증서 교체는 명시한 지문만 허용하고 잘못된 설정은 기동 시 거부한다`() {
        val replacement = "ab".repeat(32)
        val replacementDigest = Base64.getUrlEncoder().encodeToString(HexFormat.of().parseHex(replacement))
        assertEquals(AttestationVerdict.CERTIFICATE_MISMATCH, verifier.verify(response(replacementDigest), REQUEST_HASH, now))
        val rotated = PlayIntegrityVerdictVerifier("com.ssutoday", "$certificate,$replacement")
        assertEquals(AttestationVerdict.VERIFIED, rotated.verify(response(replacementDigest), REQUEST_HASH, now))
        assertEquals(AttestationVerdict.VERIFIED, rotated.verify(response(), REQUEST_HASH, now))
        assertFailsWith<IllegalArgumentException> { PlayIntegrityVerdictVerifier("com.ssutoday", "") }
        assertFailsWith<IllegalArgumentException> { PlayIntegrityVerdictVerifier("com.ssutoday", "garbage") }
        assertFailsWith<IllegalArgumentException> { PlayIntegrityVerdictVerifier("com.ssutoday/other", certificate) }
    }

    private fun response(certificateDigest: String = digest): ObjectNode =
        mapper.readTree(
            """
            {"tokenPayloadExternal": {
              "requestDetails": {"requestPackageName":"com.ssutoday", "requestHash":"$REQUEST_HASH", "timestampMillis":"${now.toEpochMilli()}"},
              "appIntegrity": {"appRecognitionVerdict":"PLAY_RECOGNIZED", "packageName":"com.ssutoday", "certificateSha256Digest":["$certificateDigest"], "versionCode":"42"},
              "deviceIntegrity": {"deviceRecognitionVerdict":["MEETS_BASIC_INTEGRITY", "MEETS_DEVICE_INTEGRITY"]},
              "accountDetails": {"appLicensingVerdict":"LICENSED"}
            }}
            """.trimIndent(),
        ) as ObjectNode

    private companion object {
        const val REQUEST_HASH = "bF7nJGb7o2lT2ogovdDp0VPtyO_xoHxNgqb3OaAcDaU"
    }
}
