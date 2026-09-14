package kr.ac.ssu.ssutoday.adapter.attestation

import kr.ac.ssu.ssutoday.core.attestation.AttestationVerdict
import tools.jackson.databind.JsonNode
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.HexFormat

/** Google decode 응답만 입력한다. 클라이언트가 보낸 JSON을 이 검증기에 전달하면 안 된다. */
internal class PlayIntegrityVerdictVerifier(
    private val packageName: String,
    certificateSha256: String,
) {
    private val certificates =
        certificateSha256.split(',').map { fingerprint ->
            val hex = fingerprint.trim().replace(":", "")
            require(Regex("[0-9a-fA-F]{64}").matches(hex)) { "Invalid Play Integrity signing certificate configuration" }
            HexFormat.of().parseHex(hex)
        }

    init {
        require(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+").matches(packageName)) {
            "Invalid Play Integrity package configuration"
        }
    }

    fun verify(
        response: JsonNode,
        requestHash: String,
        now: Instant,
    ): AttestationVerdict {
        val payload = response.path("tokenPayloadExternal")
        val request = payload.path("requestDetails")
        if (!payload.isObject || !request.isObject) return AttestationVerdict.INVALID_RESPONSE
        if (request.text("requestPackageName") != packageName) return AttestationVerdict.PACKAGE_MISMATCH
        if (request.text("requestHash") != requestHash) return AttestationVerdict.REQUEST_HASH_MISMATCH

        val timestamp = request.text("timestampMillis")?.toLongOrNull() ?: return AttestationVerdict.INVALID_RESPONSE
        val nowMillis = now.toEpochMilli()
        if (timestamp < nowMillis - MAX_TOKEN_AGE_MILLIS) return AttestationVerdict.TOKEN_EXPIRED
        if (timestamp > nowMillis + FUTURE_SKEW_MILLIS) return AttestationVerdict.TOKEN_FROM_FUTURE

        val app = payload.path("appIntegrity")
        if (app.text("appRecognitionVerdict") != "PLAY_RECOGNIZED") return AttestationVerdict.APP_UNRECOGNIZED
        if (app.text("packageName") != packageName) return AttestationVerdict.PACKAGE_MISMATCH
        if (!hasTrustedCertificate(app.path("certificateSha256Digest"))) return AttestationVerdict.CERTIFICATE_MISMATCH

        val deviceVerdicts = payload.path("deviceIntegrity").path("deviceRecognitionVerdict")
        if (!deviceVerdicts.isArray || deviceVerdicts.none { it.isString && it.asString() == "MEETS_DEVICE_INTEGRITY" }) {
            return AttestationVerdict.DEVICE_UNTRUSTED
        }
        return AttestationVerdict.VERIFIED
    }

    private fun hasTrustedCertificate(digests: JsonNode): Boolean {
        if (!digests.isArray || digests.isEmpty) return false
        // 허용한 인증서로만 서명된 앱을 인정한다. 키 교체 기간에는 설정에 양쪽 지문을 넣는다.
        return digests.all { digest ->
            if (!digest.isString) return@all false
            val decoded =
                try {
                    Base64.getUrlDecoder().decode(digest.asString())
                } catch (_: IllegalArgumentException) {
                    return@all false
                }
            certificates.any { MessageDigest.isEqual(it, decoded) }
        }
    }

    private fun JsonNode.text(field: String): String? = path(field).takeIf { it.isString }?.asString()

    private companion object {
        const val MAX_TOKEN_AGE_MILLIS = 120_000L
        const val FUTURE_SKEW_MILLIS = 30_000L
    }
}
