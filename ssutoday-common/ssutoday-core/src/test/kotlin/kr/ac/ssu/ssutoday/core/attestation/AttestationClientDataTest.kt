package kr.ac.ssu.ssutoday.core.attestation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class AttestationClientDataTest {
    private val challenge = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8"
    private val photoHash = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

    @Test
    fun `사진 업로드 바이트와 해시는 플랫폼 공통 벡터와 일치한다`() {
        val data = AttestationClientData.forPhotoUpload(20260000, 42, challenge, photoHash)

        assertEquals(
            "ssutoday.attestation.v1\npurpose=VERIFY_PHOTO_UPLOAD\nstudentId=20260000\n" +
                "reservationId=42\nchallenge=$challenge\nphotoSha256=$photoHash\n",
            data.toString(Charsets.UTF_8),
        )
        assertEquals("bF7nJGb7o2lT2ogovdDp0VPtyO_xoHxNgqb3OaAcDaU", AttestationClientData.requestHash(data))
        assertEquals(32, AttestationClientData.hash(data).size)
    }

    @Test
    fun `등록은 keyId와 별도 용도에 바인딩한다`() {
        val data = AttestationClientData.forAppAttestRegistration(20260000, challenge, "$challenge=")

        assertEquals("pA9dtkbbjzQnPIvkgb0Le6Bjf5ThvCbeXzTpQNLbImY", AttestationClientData.requestHash(data))
        assertNotEquals(
            AttestationClientData.requestHash(data),
            AttestationClientData.requestHash(AttestationClientData.forPhotoUpload(20260000, 42, challenge, photoHash)),
        )
    }

    @Test
    fun `학생 예약 사진 challenge 중 하나가 바뀌면 다른 요청이다`() {
        val original = AttestationClientData.requestHash(AttestationClientData.forPhotoUpload(20260000, 42, challenge, photoHash))
        val changed =
            listOf(
                AttestationClientData.forPhotoUpload(20260001, 42, challenge, photoHash),
                AttestationClientData.forPhotoUpload(20260000, 43, challenge, photoHash),
                AttestationClientData.forPhotoUpload(20260000, 42, "A".repeat(43), photoHash),
                AttestationClientData.forPhotoUpload(20260000, 42, challenge, "0".repeat(64)),
            )

        changed.forEach { assertNotEquals(original, AttestationClientData.requestHash(it)) }
    }

    @Test
    fun `비정규 Base64와 개행 또는 대문자 사진 해시는 허용하지 않는다`() {
        assertFalse(AttestationClientData.isValidChallenge(challenge.dropLast(1) + "9"))
        assertFalse(AttestationClientData.isValidChallenge("$challenge="))
        assertFailsWith<IllegalArgumentException> {
            AttestationClientData.forPhotoUpload(20260000, 42, "$challenge\n", photoHash)
        }
        assertFailsWith<IllegalArgumentException> {
            AttestationClientData.forPhotoUpload(20260000, 42, challenge, photoHash.uppercase())
        }
        assertFailsWith<IllegalArgumentException> {
            AttestationClientData.forAppAttestRegistration(20260000, challenge, "$challenge\n")
        }
    }
}
