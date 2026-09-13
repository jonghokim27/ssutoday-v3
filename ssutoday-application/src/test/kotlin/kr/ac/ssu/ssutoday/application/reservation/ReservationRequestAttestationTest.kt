package kr.ac.ssu.ssutoday.application.reservation

import kr.ac.ssu.ssutoday.application.attest.AttestationVerificationApplicationService
import kr.ac.ssu.ssutoday.application.attest.dto.AttestationEvidence
import kr.ac.ssu.ssutoday.application.reservation.dto.CreateReservationCommand
import kr.ac.ssu.ssutoday.core.attestation.AttestationVerdict
import kr.ac.ssu.ssutoday.core.exception.BusinessException
import kr.ac.ssu.ssutoday.core.port.AppAttestVerificationPort
import kr.ac.ssu.ssutoday.core.port.DiscordAttestationNotificationPort
import kr.ac.ssu.ssutoday.core.port.DiscordReservationActionNotificationPort
import kr.ac.ssu.ssutoday.core.port.PlayIntegrityVerificationPort
import kr.ac.ssu.ssutoday.core.port.PushMessagePublisher
import kr.ac.ssu.ssutoday.core.port.ReservationRequestPublisher
import kr.ac.ssu.ssutoday.core.port.TurnstileVerificationPort
import kr.ac.ssu.ssutoday.core.status.StatusCode
import kr.ac.ssu.ssutoday.domain.config.ConfigService
import kr.ac.ssu.ssutoday.domain.reservation.ReservationCompletionPolicy
import kr.ac.ssu.ssutoday.domain.reservation.ReservationRequestPolicy
import kr.ac.ssu.ssutoday.domain.reservation.ReservationRequestService
import kr.ac.ssu.ssutoday.domain.reservation.ReservationService
import kr.ac.ssu.ssutoday.domain.reservation.VerifyPhotoService
import kr.ac.ssu.ssutoday.domain.room.RoomService
import kr.ac.ssu.ssutoday.domain.room.RoomView
import kr.ac.ssu.ssutoday.domain.student.AttestChallengeService
import kr.ac.ssu.ssutoday.domain.student.DeviceAttestationService
import kr.ac.ssu.ssutoday.domain.student.DeviceService
import kr.ac.ssu.ssutoday.domain.student.StudentService
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReservationRequestAttestationTest {
    private val requests = mock(ReservationRequestService::class.java)
    private val reservations = mock(ReservationService::class.java)
    private val rooms = mock(RoomService::class.java)
    private val turnstile = mock(TurnstileVerificationPort::class.java)
    private val publisher = mock(ReservationRequestPublisher::class.java)
    private val discord = mock(DiscordAttestationNotificationPort::class.java)
    private val provider = mock(PlayIntegrityVerificationPort::class.java)
    private val command = CreateReservationCommand("turnstile", 20260000, "cse", false, "1", LocalDate.of(2026, 9, 14), 20, 23)
    private val evidence = AttestationEvidence("android", "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8", "opaque-token")

    init {
        `when`(turnstile.verify("turnstile")).thenReturn(true)
        `when`(rooms.get("1", "cse", false)).thenReturn(RoomView("1", "Room", "cse", 4, "", "", "", "", true))
        `when`(requests.create(20260000, "1", command.date, 20, 23)).thenReturn(42)
        `when`(provider.verify(anyString(), anyString())).thenReturn(AttestationVerdict.DEVICE_UNTRUSTED)
    }

    @Test
    fun `관찰 모드는 구버전과 실패 요청을 접수하고 커밋 이후 Discord에 판정만 전송한다`() {
        TransactionSynchronizationManager.initSynchronization()
        try {
            assertEquals(42L, service(false).createReservationRequest(command))
            assertEquals(42L, service(false).createReservationRequest(command.copy(attestation = evidence)))
            verifyNoInteractions(publisher, discord)
            TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }
            verify(discord).sendReservationResult(20260000, 42, AttestationVerdict.MISSING, false)
            verify(discord).sendReservationResult(20260000, 42, AttestationVerdict.DEVICE_UNTRUSTED, false)
        } finally {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    @Test
    fun `강제 모드는 구버전과 실패 요청을 DB 생성과 큐 발행 전에 거부한다`() {
        listOf(command, command.copy(attestation = evidence)).forEach {
            assertEquals(StatusCode.SSU4206, assertFailsWith<BusinessException> { service(true).createReservationRequest(it) }.status)
        }
        verifyNoInteractions(requests, publisher, discord)
    }

    @Test
    fun `관찰 모드여도 기존 Turnstile 검증을 우회하지 않는다`() {
        `when`(turnstile.verify("turnstile")).thenReturn(false)
        assertEquals(StatusCode.SSU4092, assertFailsWith<BusinessException> { service(false).createReservationRequest(command) }.status)
        verifyNoInteractions(requests, provider, publisher, discord, rooms)
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

    private fun service(enforce: Boolean) =
        ReservationCommandApplicationService(
            requests,
            reservations,
            mock(VerifyPhotoService::class.java),
            rooms,
            mock(StudentService::class.java),
            mock(DeviceService::class.java),
            mock(ConfigService::class.java),
            mock(ReservationRequestPolicy::class.java),
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
        )
}
