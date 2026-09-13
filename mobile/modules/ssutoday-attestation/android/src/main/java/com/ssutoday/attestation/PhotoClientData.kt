package com.ssutoday.attestation

import java.security.MessageDigest

internal object PhotoClientData {
  private val challengePattern = Regex("[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]")

  fun requestHashBytes(studentId: Int, reservationId: Long, challenge: String, photoSha256: String): ByteArray {
    require(studentId > 0 && reservationId > 0)
    require(challengePattern.matches(challenge))
    require(Regex("[0-9a-f]{64}").matches(photoSha256))
    val data = "ssutoday.attestation.v1\n" +
      "purpose=VERIFY_PHOTO_UPLOAD\n" +
      "studentId=$studentId\n" +
      "reservationId=$reservationId\n" +
      "challenge=$challenge\n" +
      "photoSha256=$photoSha256\n"
    return MessageDigest.getInstance("SHA-256").digest(data.toByteArray(Charsets.UTF_8))
  }

  fun photoHash(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
