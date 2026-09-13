package kr.ac.ssu.ssutoday.core.attestation

import java.security.MessageDigest
import java.util.Base64

/** 네이티브와 서버가 독립적으로 재구성하는 v1 요청 바이트. docs/attestation.md 참고. */
object AttestationClientData {
    private val challengePattern = Regex("[A-Za-z0-9_-]{43}")
    private val photoHashPattern = Regex("[0-9a-f]{64}")

    fun isValidKeyId(keyId: String): Boolean =
        try {
            keyId.length == 44 &&
                Base64.getDecoder().decode(keyId).let {
                    it.size == 32 && Base64.getEncoder().encodeToString(it) == keyId
                }
        } catch (_: IllegalArgumentException) {
            false
        }

    fun isValidChallenge(challenge: String): Boolean {
        if (!challengePattern.matches(challenge)) return false
        val decoded = Base64.getUrlDecoder().decode(challenge)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(decoded) == challenge
    }

    fun forPhotoUpload(
        studentId: Int,
        reservationId: Long,
        challenge: String,
        photoSha256: String,
    ): ByteArray {
        require(studentId > 0 && reservationId > 0)
        require(isValidChallenge(challenge))
        require(photoHashPattern.matches(photoSha256))
        return encode(
            "purpose=VERIFY_PHOTO_UPLOAD",
            "studentId=$studentId",
            "reservationId=$reservationId",
            "challenge=$challenge",
            "photoSha256=$photoSha256",
        )
    }

    fun forAppAttestRegistration(
        studentId: Int,
        challenge: String,
        keyId: String,
    ): ByteArray {
        require(studentId > 0)
        require(isValidChallenge(challenge))
        // Apple keyId는 32바이트 공개키 해시의 표준 Base64(padding 포함) 표현이다.
        require(keyId.length == 44)
        val decodedKeyId = Base64.getDecoder().decode(keyId)
        require(decodedKeyId.size == 32 && Base64.getEncoder().encodeToString(decodedKeyId) == keyId)
        return encode(
            "purpose=APP_ATTEST_REGISTER",
            "studentId=$studentId",
            "challenge=$challenge",
            "keyId=$keyId",
        )
    }

    fun hash(clientData: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(clientData)

    fun requestHash(clientData: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(hash(clientData))

    private fun encode(vararg fields: String): ByteArray =
        ("ssutoday.attestation.v1\n" + fields.joinToString("\n") + "\n").toByteArray(Charsets.UTF_8)
}
