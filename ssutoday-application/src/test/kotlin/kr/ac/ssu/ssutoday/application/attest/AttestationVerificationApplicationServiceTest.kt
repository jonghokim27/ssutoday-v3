package kr.ac.ssu.ssutoday.application.attest

import kr.ac.ssu.ssutoday.application.attest.dto.AttestationEvidence
import kr.ac.ssu.ssutoday.application.attest.dto.VerifyPhotoAttestationCommand
import kr.ac.ssu.ssutoday.core.attestation.AttestationChallengeScope
import kr.ac.ssu.ssutoday.core.attestation.AttestationClientData
import kr.ac.ssu.ssutoday.core.attestation.AttestationPurpose
import kr.ac.ssu.ssutoday.core.attestation.AttestationVerdict
import kr.ac.ssu.ssutoday.core.exception.BusinessException
import kr.ac.ssu.ssutoday.core.port.AppAttestVerificationPort
import kr.ac.ssu.ssutoday.core.port.AttestationChallengeStorePort
import kr.ac.ssu.ssutoday.core.port.PlayIntegrityVerificationPort
import kr.ac.ssu.ssutoday.core.status.StatusCode
import kr.ac.ssu.ssutoday.domain.student.AttestChallengeService
import kr.ac.ssu.ssutoday.domain.student.DeviceAttestationService
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.springframework.dao.DataAccessResourceFailureException
import java.time.Duration
import java.util.HexFormat
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AttestationVerificationApplicationServiceTest {
    private val scope = AttestationChallengeScope(20260000, AttestationPurpose.VERIFY_PHOTO_UPLOAD, 42)
    private val stored = ConcurrentHashMap<String, AttestationChallengeScope>()
    private val store =
        object : AttestationChallengeStorePort {
            override fun create(
                challenge: String,
                scope: AttestationChallengeScope,
                ttl: Duration,
            ): Boolean = stored.putIfAbsent(challenge, scope) == null

            override fun consume(
                challenge: String,
                scope: AttestationChallengeScope,
            ): Boolean = stored.remove(challenge, scope)
        }
    private val challenges = AttestChallengeService(store)
    private val challenge = challenges.create(scope).challenge
    private val evidence = AttestationEvidence("android", challenge, "opaque-token")
    private val photo = "camera-photo".toByteArray()
    private val command = VerifyPhotoAttestationCommand(scope.studentId, 42, photo, evidence)
    private val calls = mutableListOf<Pair<String, String>>()
    private var providerVerdict = AttestationVerdict.VERIFIED
    private val expectedHash =
        AttestationClientData.requestHash(
            AttestationClientData.forPhotoUpload(
                scope.studentId,
                42,
                challenge,
                HexFormat.of().formatHex(AttestationClientData.hash(photo)),
            ),
        )
    private val provider =
        object : PlayIntegrityVerificationPort {
            override fun verify(
                token: String,
                requestHash: String,
            ): AttestationVerdict {
                calls += token to requestHash
                if (requestHash != expectedHash) return AttestationVerdict.REQUEST_HASH_MISMATCH
                return providerVerdict
            }
        }

    @Test
    fun `사진 교체 학생 예약과 challenge 변경은 요청 해시 불일치로 거부한다`() {
        val changed =
            listOf(
                command.copy(photo = "replaced-photo".toByteArray()),
                command.copy(studentId = 20260001),
                command.copy(reservationId = 43),
                command.copy(evidence = evidence.copy(challenge = challenges.create(scope).challenge)),
            )
        changed.forEach {
            assertEquals(AttestationVerdict.REQUEST_HASH_MISMATCH, service(false).verify(it).verdict)
            assertTrue(stored.containsKey(challenge))
        }
        assertEquals(AttestationVerdict.VERIFIED, service(true).verify(command).verdict)
        assertFalse(stored.containsKey(challenge))
    }

    @Test
    fun `관찰 모드도 정상 증명은 소모하며 재사용을 성공으로 기록하지 않는다`() {
        val service = service(false)
        assertEquals(AttestationVerdict.VERIFIED, service.verify(command).verdict)
        assertEquals(AttestationVerdict.CHALLENGE_REJECTED, service.verify(command).verdict)
        assertEquals(StatusCode.SSU4206, assertFailsWith<BusinessException> { service(true).verify(command) }.status)
    }

    @Test
    fun `Google 검증 성공이어도 challenge의 학생 예약 용도가 다르거나 만료되면 거부한다`() {
        listOf(
            scope.copy(studentId = 20260001),
            scope.copy(reservationId = 43),
            AttestationChallengeScope(scope.studentId, AttestationPurpose.APP_ATTEST_REGISTER),
        ).forEach { otherScope ->
            stored[challenge] = otherScope
            assertEquals(AttestationVerdict.CHALLENGE_REJECTED, service(false).verify(command).verdict)
            assertEquals(otherScope, stored[challenge])
        }
        stored.clear()
        assertEquals(AttestationVerdict.CHALLENGE_REJECTED, service(false).verify(command).verdict)
    }

    @Test
    fun `구버전과 부분 입력 미지원 플랫폼은 관찰 모드에서 분류하고 강제 모드에서 거부한다`() {
        val cases =
            listOf(
                AttestationEvidence() to AttestationVerdict.MISSING,
                AttestationEvidence(platform = "android") to AttestationVerdict.INVALID_INPUT,
                evidence.copy(attestation = " ") to AttestationVerdict.INVALID_INPUT,
                evidence.copy(attestation = "a".repeat(32 * 1024 + 1)) to AttestationVerdict.INVALID_INPUT,
                evidence.copy(challenge = "malformed") to AttestationVerdict.INVALID_INPUT,
                evidence.copy(keyId = "unexpected-key") to AttestationVerdict.INVALID_INPUT,
                evidence.copy(platform = "ios") to AttestationVerdict.INVALID_INPUT,
                evidence.copy(platform = "web\ninjected-log") to AttestationVerdict.UNSUPPORTED_PLATFORM,
            )
        cases.forEach { (input, verdict) ->
            val result = service(false).verify(command.copy(evidence = input))
            assertEquals(verdict, result.verdict)
            assertFalse(result.platform.contains("\n"))
            assertEquals(
                StatusCode.SSU4206,
                assertFailsWith<BusinessException> { service(true).verify(command.copy(evidence = input)) }.status,
            )
        }
        assertTrue(calls.isEmpty())
        assertTrue(stored.containsKey(challenge))
    }

    @Test
    fun `외부 검증 실패는 관찰 모드에서 기록하고 강제 모드에서 소모 전에 거부한다`() {
        listOf(
            AttestationVerdict.APP_UNRECOGNIZED,
            AttestationVerdict.CERTIFICATE_MISMATCH,
            AttestationVerdict.DEVICE_UNTRUSTED,
            AttestationVerdict.TOKEN_REJECTED,
            AttestationVerdict.NOT_CONFIGURED,
            AttestationVerdict.PROVIDER_UNAVAILABLE,
            AttestationVerdict.PROVIDER_AUTHORIZATION_FAILED,
            AttestationVerdict.INVALID_RESPONSE,
        ).forEach { verdict ->
            providerVerdict = verdict
            assertEquals(verdict, service(false).verify(command).verdict)
            assertEquals(StatusCode.SSU4206, assertFailsWith<BusinessException> { service(true).verify(command) }.status)
            assertTrue(stored.containsKey(challenge))
        }
    }

    @Test
    fun `Redis 장애도 관찰 모드에서는 업로드를 허용하고 강제 모드에서는 거부한다`() {
        val unavailable = mock(AttestChallengeService::class.java)
        doThrow(DataAccessResourceFailureException("redis unavailable")).`when`(unavailable).consume(challenge, scope)
        assertEquals(
            AttestationVerdict.CHALLENGE_STORE_UNAVAILABLE,
            AttestationVerificationApplicationService(
                provider,
                unavailable,
                false,
                mock(AppAttestVerificationPort::class.java),
                mock(DeviceAttestationService::class.java),
                true,
            ).verify(command).verdict,
        )
        assertEquals(
            StatusCode.SSU4206,
            assertFailsWith<BusinessException> {
                AttestationVerificationApplicationService(
                    provider,
                    unavailable,
                    true,
                    mock(AppAttestVerificationPort::class.java),
                    mock(DeviceAttestationService::class.java),
                    true,
                ).verify(command)
            }.status,
        )
    }

    private fun service(enforce: Boolean) =
        AttestationVerificationApplicationService(
            provider,
            challenges,
            enforce,
            mock(AppAttestVerificationPort::class.java),
            mock(DeviceAttestationService::class.java),
            true,
        )
}
