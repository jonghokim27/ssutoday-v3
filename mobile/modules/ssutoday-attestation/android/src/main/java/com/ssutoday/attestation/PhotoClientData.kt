package com.ssutoday.attestation

import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale

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

  fun reservationHashBytes(studentId: Int, roomNo: String, date: String, startBlock: Int, endBlock: Int, challenge: String): ByteArray {
    require(studentId > 0 && roomNo.isNotBlank() && roomNo.length <= 100)
    require(startBlock in 12..43 && endBlock in startBlock..43 && challengePattern.matches(challenge))
    require(Regex("202[3-9]-[0-9]{2}-[0-9]{2}").matches(date))
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }
    require(formatter.format(requireNotNull(formatter.parse(date))) == date)
    val room = roomNo.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }
    val data = "ssutoday.attestation.v1\npurpose=RESERVATION_CREATE\nstudentId=$studentId\nroomNoUtf8Hex=$room\ndate=$date\nstartBlock=$startBlock\nendBlock=$endBlock\nchallenge=$challenge\n"
    return MessageDigest.getInstance("SHA-256").digest(data.toByteArray(Charsets.UTF_8))
  }
}
