package kr.ac.ssu.ssutoday.application.reservation

import kr.ac.ssu.ssutoday.application.attest.AttestationVerificationApplicationService
import kr.ac.ssu.ssutoday.application.attest.dto.AttestationEvidence
import kr.ac.ssu.ssutoday.application.reservation.dto.CreateReservationCommand
import kr.ac.ssu.ssutoday.core.attestation.AttestationVerdict
import kr.ac.ssu.ssutoday.core.attestation.PlayIntegritySignals
import kr.ac.ssu.ssutoday.core.attestation.PlayIntegrityVerification
import kr.ac.ssu.ssutoday.core.attestation.RiskFlag
import kr.ac.ssu.ssutoday.core.exception.BusinessException
import kr.ac.ssu.ssutoday.core.port.AppAttestVerificationPort
import kr.ac.ssu.ssutoday.core.port.DiscordAttestationNotificationPort
import kr.ac.ssu.ssutoday.core.port.DiscordReservationActionNotificationPort
import kr.ac.ssu.ssutoday.core.port.PlayIntegrityVerificationPort
import kr.ac.ssu.ssutoday.core.port.PushMessagePublisher
import kr.ac.ssu.ssutoday.core.port.ReservationRequestPublisher
import kr.ac.ssu.ssutoday.core.port.TurnstileVerificationPort
import kr.ac.ssu.ssutoday.core.status.StatusCode
import kr.ac.ssu.ssutoday.core.turnstile.TurnstileAction
import kr.ac.ssu.ssutoday.core.turnstile.TurnstileVerification
import kr.ac.ssu.ssutoday.domain.config.ConfigService
import kr.ac.ssu.ssutoday.domain.reservation.ReservationCompletionPolicy
import kr.ac.ssu.ssutoday.domain.reservation.ReservationRequestPolicy
import kr.ac.ssu.ssutoday.domain.reservation.ReservationRequestService
import kr.ac.ssu.ssutoday.domain.reservation.ReservationRequestSignal
import kr.ac.ssu.ssutoday.domain.reservation.ReservationRequestSignalRepository
import kr.ac.ssu.ssutoday.domain.reservation.ReservationRequestSignalService
import kr.ac.ssu.ssutoday.domain.reservation.ReservationRiskPolicy
import kr.ac.ssu.ssutoday.domain.reservation.ReservationService
import kr.ac.ssu.ssutoday.domain.reservation.VerifyPhotoService
import kr.ac.ssu.ssutoday.domain.room.RoomService
import kr.ac.ssu.ssutoday.domain.room.RoomView
import kr.ac.ssu.ssutoday.domain.student.AttestChallengeService
import kr.ac.ssu.ssutoday.domain.student.DeviceAttestationService
import kr.ac.ssu.ssutoday.domain.student.DeviceService
import kr.ac.ssu.ssutoday.domain.student.StudentService
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReservationRequestAttestationTest {
    private val requests = mock(ReservationRequestService::class.java)
    private val reservations = mock(ReservationService::class.java)
    private val rooms = mock(RoomService::class.java)
    private val turnstile = mock(TurnstileVerificationPort::class.java)
    private val publisher = mock(ReservationRequestPublisher::class.java)
    private val discord = mock(DiscordAttestationNotificationPort::class.java)
    private val provider = mock(PlayIntegrityVerificationPort::class.java)
    private val signalRepository = mock(ReservationRequestSignalRepository::class.java)
    private val command = CreateReservationCommand("turnstile", 20260000, "cse", false, "1", LocalDate.of(2026, 9, 14), 20, 23)
    private val evidence = AttestationEvidence("android", "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8", "opaque-token")
    private val openAt =
        LocalDate
            .of(2026, 9, 13)
            .atTime(20, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()

    init {
        solvedAt(openAt.plusSeconds(1))
        `when`(rooms.get("1", "cse", false)).thenReturn(RoomView("1", "Room", "cse", 4, "", "", "", "", true))
        `when`(rooms.get("1", "cse", true)).thenReturn(RoomView("1", "Room", "cse", 4, "", "", "", "", true))
        `when`(requests.create(20260000, "1", command.date, 20, 23)).thenReturn(42)
        `when`(provider.verify(anyString(), anyString())).thenReturn(PlayIntegrityVerification(AttestationVerdict.DEVICE_UNTRUSTED))
    }

    @Test
    fun `관찰 모드는 구버전과 실패 요청을 접수하고 커밋 이후 Discord에 판정만 전송한다`() {
        TransactionSynchronizationManager.initSynchronization()
        try {
            assertEquals(42L, service(false).createReservationRequest(command))
            assertEquals(42L, service(false).createReservationRequest(command.copy(attestation = evidence)))
            verifyNoInteractions(publisher, discord)
            TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }
            verify(discord).sendReservationResult(20260000, 42, AttestationVerdict.MISSING, false, emptySet())
            verify(discord).sendReservationResult(20260000, 42, AttestationVerdict.DEVICE_UNTRUSTED, false, emptySet())
        } finally {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    @Test
    fun `강제 모드는 구버전과 실패 요청을 DB 생성과 큐 발행 전에 거부한다`() {
        listOf(command, command.copy(attestation = evidence)).forEach {
            assertEquals(StatusCode.SSU4206, assertFailsWith<BusinessException> { service(true).createReservationRequest(it) }.status)
        }
        verifyNoInteractions(requests, signalRepository, publisher, discord)
    }

    @Test
    fun `관찰 모드여도 기존 Turnstile 검증을 우회하지 않는다`() {
        `when`(turnstile.verify("turnstile", TurnstileAction.RESERVATION_REQUEST)).thenReturn(null)
        assertEquals(StatusCode.SSU4092, assertFailsWith<BusinessException> { service(false).createReservationRequest(command) }.status)
        verifyNoInteractions(requests, provider, signalRepository, publisher, discord, rooms)
    }

    @Test
    fun `예약 오픈 전에 푼 Turnstile 토큰은 저장 전에 거부한다`() {
        solvedAt(openAt.minusMillis(1))
        assertEquals(StatusCode.SSU4093, assertFailsWith<BusinessException> { service(false).createReservationRequest(command) }.status)
        verifyNoInteractions(requests, provider, signalRepository, publisher, discord, rooms)

        solvedAt(openAt)
        assertEquals(42L, service(false).createReservationRequest(command))
    }

    @Test
    fun `관리자는 오픈 전에 푼 토큰으로도 예약할 수 있다`() {
        solvedAt(openAt.minusSeconds(3_600))
        `when`(requests.create(20260000, "1", command.date, 20, 23)).thenReturn(43)
        assertEquals(43L, service(false).createReservationRequest(command.copy(admin = true)))
    }

    @Test
    fun `검토 신호를 요청과 함께 저장하고 해당하는 신호를 모두 알린다`() {
        val signals = PlayIntegritySignals(appsDetected = listOf("UNKNOWN_CONTROLLING"), appLicensingVerdict = "UNLICENSED")
        `when`(provider.verify(anyString(), anyString())).thenReturn(PlayIntegrityVerification(AttestationVerdict.VERIFIED, signals))
        TransactionSynchronizationManager.initSynchronization()
        try {
            val before = Instant.now()
            service(true).createReservationRequest(command.copy(attestation = evidence))
            val after = Instant.now()

            val saved = ArgumentCaptor.forClass(ReservationRequestSignal::class.java)
            verify(signalRepository).save(saved.capture())
            with(saved.value) {
                assertEquals(42L, requestId)
                assertEquals(20260000, studentId)
                assertTrue(receivedAt.toInstant() in before..after)
                assertEquals(receivedAt.toInstant().toEpochMilli() - openAt.toEpochMilli(), openOffsetMillis)
                assertEquals(openAt.plusSeconds(1), turnstileSolvedAt.toInstant())
                assertEquals("android", platform)
                assertEquals("VERIFIED", verdict)
                assertEquals("UNKNOWN_CONTROLLING", appsDetected)
                assertEquals(false, strongIntegrity)
                assertEquals("UNLICENSED", appLicensingVerdict)
                assertNull(playProtectVerdict)
                assertEquals("ACCESS_RISK,UNLICENSED", flags)
            }

            TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }
            verify(discord).sendReservationResult(
                20260000,
                42,
                AttestationVerdict.VERIFIED,
                true,
                setOf(RiskFlag.ACCESS_RISK, RiskFlag.UNLICENSED),
            )
        } finally {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    @Test
    fun `롤백하면 접수 알림과 메시지를 발행하지 않는다`() {
        TransactionSynchronizationManager.initSynchronization()
        try {
            service(false).createReservationRequest(command)
            TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCompletion(1) }
            verifyNoInteractions(publisher, discord)
        } finally {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    private fun solvedAt(instant: Instant) {
        `when`(turnstile.verify("turnstile", TurnstileAction.RESERVATION_REQUEST)).thenReturn(TurnstileVerification(instant))
    }

    private fun service(enforce: Boolean) =
        ReservationCommandApplicationService(
            requests,
            reservations,
            mock(VerifyPhotoService::class.java),
            rooms,
            mock(StudentService::class.java),
            mock(DeviceService::class.java),
            mock(ConfigService::class.java),
            ReservationRequestPolicy(),
            mock(ReservationCompletionPolicy::class.java),
            publisher,
            mock(PushMessagePublisher::class.java),
            turnstile,
            mock(DiscordReservationActionNotificationPort::class.java),
            AttestationVerificationApplicationService(
                provider,
                mock(AttestChallengeService::class.java),
                enforce,
                mock(AppAttestVerificationPort::class.java),
                mock(DeviceAttestationService::class.java),
                true,
            ),
            discord,
            ReservationRequestSignalService(signalRepository),
            ReservationRiskPolicy(),
        )
}
