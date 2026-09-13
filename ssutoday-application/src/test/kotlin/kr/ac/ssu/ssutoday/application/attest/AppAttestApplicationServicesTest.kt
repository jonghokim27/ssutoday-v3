package kr.ac.ssu.ssutoday.application.attest

import kr.ac.ssu.ssutoday.application.attest.dto.PhotoAttestationEvidence
import kr.ac.ssu.ssutoday.application.attest.dto.RegisterAppAttestCommand
import kr.ac.ssu.ssutoday.application.attest.dto.VerifyPhotoAttestationCommand
import kr.ac.ssu.ssutoday.core.attestation.AppAttestAssertionVerification
import kr.ac.ssu.ssutoday.core.attestation.AppAttestRegistrationVerification
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
import kr.ac.ssu.ssutoday.domain.student.DeviceAttestationView
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.Duration
import java.util.Base64
import java.util.HexFormat
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppAttestApplicationServicesTest {
    private val stored = mutableMapOf<String, AttestationChallengeScope>()
    private val challenges =
        AttestChallengeService(
            object : AttestationChallengeStorePort {
                override fun create(
                    challenge: String,
                    scope: AttestationChallengeScope,
                    ttl: Duration,
                ) = (
                    stored.putIfAbsent(challenge, scope) ==
                        null
                )

                override fun consume(
                    challenge: String,
                    scope: AttestationChallengeScope,
                ) = stored.remove(challenge, scope)
            },
        )
    private val keyId = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })
    private val studentId = 20260000
    private val publicKey = byteArrayOf(1, 2, 3)
    private var key: DeviceAttestationView? = null
    private var registrationCalls = 0
    private var assertionCalls = 0
    private var counter = 1L
    private var registrationVerdict = AttestationVerdict.VERIFIED
    private var expectedAssertionHash: ByteArray? = null
    private var registeredClientHash: ByteArray? = null
    private val verifier =
        object : AppAttestVerificationPort {
            override fun verifyRegistration(
                attestation: String,
                keyId: String,
                clientDataHash: ByteArray,
            ): AppAttestRegistrationVerification {
                registrationCalls++
                registeredClientHash = clientDataHash
                return AppAttestRegistrationVerification(registrationVerdict, publicKey)
            }

            override fun verifyAssertion(
                assertion: String,
                publicKey: ByteArray,
                clientDataHash: ByteArray,
            ): AppAttestAssertionVerification {
                assertionCalls++
                if (!clientDataHash.contentEquals(
                        expectedAssertionHash,
                    )
                ) {
                    return AppAttestAssertionVerification(AttestationVerdict.SIGNATURE_INVALID)
                }
                return AppAttestAssertionVerification(AttestationVerdict.VERIFIED, counter)
            }
        }
    private val keys =
        mock(DeviceAttestationService::class.java).also { service ->
            `when`(service.find(anyString())).thenAnswer { key?.takeIf { key -> key.keyId == it.getArgument<String>(0) } }
            doAnswer { invocation ->
                check(key == null)
                key =
                    DeviceAttestationView(
                        invocation.getArgument(1),
                        invocation.getArgument(0),
                        invocation.getArgument(2),
                        invocation.getArgument(3),
                        invocation.getArgument(4),
                        0,
                    )
                null
            }.`when`(service).register(
                anyInt(),
                anyString(),
                (any(ByteArray::class.java) ?: ByteArray(0)),
                anyBoolean(),
                (
                    any(ByteArray::class.java)
                        ?: ByteArray(0)
                ),
            )
            `when`(service.advanceCounter(anyString(), anyInt(), anyBoolean(), anyLong())).thenAnswer {
                val value = it.getArgument<Long>(3)
                if (key != null && key!!.counter < value) {
                    key = key!!.copy(counter = value)
                    true
                } else {
                    false
                }
            }
        }
    private val registration = AppAttestRegistrationApplicationService(verifier, keys, challenges, true)

    private fun registerCommand(): RegisterAppAttestCommand =
        RegisterAppAttestCommand(
            studentId,
            keyId,
            challenges.create(AttestationChallengeScope(studentId, AttestationPurpose.APP_ATTEST_REGISTER)).challenge,
            "YWJj",
        )

    private fun upload(): VerifyPhotoAttestationCommand {
        val challenge = challenges.create(AttestationChallengeScope(studentId, AttestationPurpose.VERIFY_PHOTO_UPLOAD, 42)).challenge
        val photo = "camera".toByteArray()
        expectedAssertionHash =
            AttestationClientData.hash(
                AttestationClientData.forPhotoUpload(studentId, 42, challenge, HexFormat.of().formatHex(AttestationClientData.hash(photo))),
            )
        return VerifyPhotoAttestationCommand(studentId, 42, photo, PhotoAttestationEvidence("ios", challenge, "YXNzZXJ0aW9u", keyId))
    }

    private fun photoService(enforce: Boolean = false) =
        PhotoAttestationApplicationService(mock(PlayIntegrityVerificationPort::class.java), challenges, enforce, verifier, keys, true)

    @Test
    fun `키 등록은 서버 client data 검증과 challenge 소모 후에만 저장한다`() {
        val command = registerCommand()
        assertEquals(keyId, registration.register(command).keyId)
        assertContentEquals(
            AttestationClientData.hash(AttestationClientData.forAppAttestRegistration(studentId, command.challenge, keyId)),
            registeredClientHash,
        )
        assertContentEquals(publicKey, key!!.publicKey)
        assertEquals(0L, key!!.counter)
        assertFalse(stored.containsKey(command.challenge))
    }

    @Test
    fun `등록 응답 유실 후 동일 payload 재전송은 만료 후에도 허용하되 카운터를 초기화하지 않는다`() {
        val command = registerCommand()
        registration.register(command)
        key = key!!.copy(counter = 7)
        assertEquals(keyId, registration.register(command).keyId)
        assertEquals(7L, key!!.counter)
        assertEquals(1, registrationCalls)
        assertEquals(
            StatusCode.SSU4206,
            assertFailsWith<BusinessException> {
                registration.register(command.copy(attestation = "ZGlmZmVyZW50"))
            }.status,
        )
        assertFailsWith<BusinessException> { registration.register(command.copy(studentId = studentId + 1)) }
        key = key!!.copy(production = false)
        assertFailsWith<BusinessException> { registration.register(command) }
    }

    @Test
    fun `검증 실패와 다른 용도 또는 만료 challenge는 키를 저장하지 않는다`() {
        val command = registerCommand()
        registrationVerdict = AttestationVerdict.NONCE_MISMATCH
        assertFailsWith<BusinessException> { registration.register(command) }
        assertTrue(stored.containsKey(command.challenge))
        assertEquals(null, key)
        registrationVerdict = AttestationVerdict.VERIFIED
        stored[command.challenge] = AttestationChallengeScope(studentId, AttestationPurpose.VERIFY_PHOTO_UPLOAD, 42)
        assertFailsWith<BusinessException> { registration.register(command) }
        stored.clear()
        assertFailsWith<BusinessException> { registration.register(command) }
        assertEquals(null, key)
    }

    @Test
    fun `iOS 사진 교체와 계정 예약 challenge 변경은 거부하고 정상 요청만 counter를 올린다`() {
        registration.register(registerCommand())
        val command = upload()
        val changes =
            listOf(
                command.copy(photo = "replaced".toByteArray()),
                command.copy(reservationId = 43),
                command.copy(evidence = command.evidence.copy(challenge = registerCommand().challenge)),
            )
        changes.forEach { assertEquals(AttestationVerdict.SIGNATURE_INVALID, photoService().verify(it).verdict) }
        assertEquals(AttestationVerdict.KEY_OWNER_MISMATCH, photoService().verify(command.copy(studentId = studentId + 1)).verdict)
        assertEquals(AttestationVerdict.VERIFIED, photoService(true).verify(command).verdict)
        assertEquals(1L, key!!.counter)
        assertFalse(stored.containsKey(command.evidence.challenge))
        assertEquals(AttestationVerdict.COUNTER_REJECTED, photoService().verify(command).verdict)
    }

    @Test
    fun `등록되지 않은 키와 다른 환경은 서명 전에 거부하고 강제 모드에서 업로드를 차단한다`() {
        val command = upload()
        assertEquals(AttestationVerdict.KEY_NOT_REGISTERED, photoService().verify(command).verdict)
        assertEquals(StatusCode.SSU4206, assertFailsWith<BusinessException> { photoService(true).verify(command) }.status)
        registration.register(registerCommand())
        key = key!!.copy(production = false)
        assertEquals(AttestationVerdict.ENVIRONMENT_MISMATCH, photoService().verify(command).verdict)
        assertEquals(0, assertionCalls)
    }

    @Test
    fun `challenge 거부 시 counter를 갱신하지 않으며 counter 경쟁도 실패로 분류한다`() {
        registration.register(registerCommand())
        val command = upload()
        stored.clear()
        assertEquals(AttestationVerdict.CHALLENGE_REJECTED, photoService().verify(command).verdict)
        assertEquals(0L, key!!.counter)
        val next = upload()
        `when`(keys.advanceCounter(keyId, studentId, true, counter)).thenReturn(false)
        assertEquals(AttestationVerdict.COUNTER_REJECTED, photoService().verify(next).verdict)
    }
}
