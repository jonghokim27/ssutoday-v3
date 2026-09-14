package kr.ac.ssu.ssutoday.application.attest

import kr.ac.ssu.ssutoday.application.attest.dto.AttestationEvidence
import kr.ac.ssu.ssutoday.application.reservation.dto.CreateReservationCommand
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
import org.mockito.Mockito.mock
import java.time.Duration
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ReservationAttestationTest {
    private val scope = AttestationChallengeScope(20260000, AttestationPurpose.RESERVATION_CREATE)
    private val stored = ConcurrentHashMap<String, AttestationChallengeScope>()
    private val challenges =
        AttestChallengeService(
            object : AttestationChallengeStorePort {
                override fun create(
                    challenge: String,
                    scope: AttestationChallengeScope,
                    ttl: Duration,
                ) = stored.putIfAbsent(challenge, scope) == null

                override fun consume(
                    challenge: String,
                    scope: AttestationChallengeScope,
                ) = stored.remove(challenge, scope)
            },
        )
    private val challenge = challenges.create(scope).challenge
    private val evidence = AttestationEvidence("android", challenge, "opaque-token")
    private val command =
        CreateReservationCommand("turnstile", scope.studentId, "cse", false, "1", LocalDate.of(2026, 9, 14), 20, 23, evidence)
    private val expectedHash =
        AttestationClientData.requestHash(
            AttestationClientData.forReservation(
                command.studentId,
                command.roomNo,
                command.date,
                command.startBlock,
                command.endBlock,
                challenge,
            ),
        )
    private var providerVerdict = AttestationVerdict.VERIFIED
    private val provider =
        object : PlayIntegrityVerificationPort {
            override fun verify(
                token: String,
                requestHash: String,
            ) = if (requestHash ==
                expectedHash
            ) {
                providerVerdict
            } else {
                AttestationVerdict.REQUEST_HASH_MISMATCH
            }
        }

    @Test
    fun `all reservation fields and the authenticated student are bound to proof`() {
        listOf(
            command.copy(roomNo = "2"),
            command.copy(date = command.date.plusDays(1)),
            command.copy(startBlock = 19),
            command.copy(endBlock = 24),
            command.copy(studentId = 20260001),
            command.copy(attestation = evidence.copy(challenge = challenges.create(scope).challenge)),
        ).forEach {
            assertEquals(AttestationVerdict.REQUEST_HASH_MISMATCH, service(false).verifyReservation(it).verdict)
            assertEquals(StatusCode.SSU4206, assertFailsWith<BusinessException> { service(true).verifyReservation(it) }.status)
            assertTrue(stored.containsKey(challenge))
        }
        assertEquals(AttestationVerdict.VERIFIED, service(true).verifyReservation(command).verdict)
        assertEquals(AttestationVerdict.CHALLENGE_REJECTED, service(false).verifyReservation(command).verdict)
    }

    @Test
    fun `photo registration other students and expired challenges cannot authorize a reservation`() {
        listOf(
            AttestationChallengeScope(scope.studentId, AttestationPurpose.VERIFY_PHOTO_UPLOAD, 42),
            AttestationChallengeScope(scope.studentId, AttestationPurpose.APP_ATTEST_REGISTER),
            scope.copy(studentId = 20260001),
        ).forEach {
            stored[challenge] = it
            assertEquals(AttestationVerdict.CHALLENGE_REJECTED, service(false).verifyReservation(command).verdict)
            assertEquals(it, stored[challenge])
        }
        stored.clear()
        assertEquals(StatusCode.SSU4206, assertFailsWith<BusinessException> { service(true).verifyReservation(command) }.status)
    }

    @Test
    fun `missing malformed and every failed provider verdict pass only in observation mode`() {
        listOf(
            AttestationEvidence(),
            evidence.copy(attestation = ""),
            evidence.copy(platform = "web"),
            evidence.copy(challenge = "bad"),
            evidence.copy(keyId = "unexpected"),
        ).forEach {
            val result = service(false).verifyReservation(command.copy(attestation = it))
            assertTrue(result.verdict != AttestationVerdict.VERIFIED)
            assertEquals(
                StatusCode.SSU4206,
                assertFailsWith<BusinessException> {
                    service(true).verifyReservation(command.copy(attestation = it))
                }.status,
            )
        }
        AttestationVerdict.entries.filter { it != AttestationVerdict.VERIFIED }.forEach {
            providerVerdict = it
            assertEquals(it, service(false).verifyReservation(command).verdict)
            assertEquals(StatusCode.SSU4206, assertFailsWith<BusinessException> { service(true).verifyReservation(command) }.status)
            assertTrue(stored.containsKey(challenge))
        }
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
