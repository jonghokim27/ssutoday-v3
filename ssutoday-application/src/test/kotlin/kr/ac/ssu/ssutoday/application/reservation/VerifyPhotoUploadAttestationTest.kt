package kr.ac.ssu.ssutoday.application.reservation

import kr.ac.ssu.ssutoday.application.attest.AttestationVerificationApplicationService
import kr.ac.ssu.ssutoday.application.attest.dto.AttestationEvidence
import kr.ac.ssu.ssutoday.application.reservation.dto.UploadPhotoCommand
import kr.ac.ssu.ssutoday.core.attestation.AttestationChallengeScope
import kr.ac.ssu.ssutoday.core.attestation.AttestationPurpose
import kr.ac.ssu.ssutoday.core.attestation.AttestationVerdict
import kr.ac.ssu.ssutoday.core.attestation.PlayIntegritySignals
import kr.ac.ssu.ssutoday.core.attestation.PlayIntegrityVerification
import kr.ac.ssu.ssutoday.core.exception.BusinessException
import kr.ac.ssu.ssutoday.core.port.AppAttestVerificationPort
import kr.ac.ssu.ssutoday.core.port.DiscordReservationActionNotificationPort
import kr.ac.ssu.ssutoday.core.port.DiscordVerifyPhotoNotificationPort
import kr.ac.ssu.ssutoday.core.port.FileStoragePort
import kr.ac.ssu.ssutoday.core.port.PlayIntegrityVerificationPort
import kr.ac.ssu.ssutoday.core.port.TokenPort
import kr.ac.ssu.ssutoday.core.port.TurnstileVerificationPort
import kr.ac.ssu.ssutoday.core.port.VerifyPhotoInspectionPort
import kr.ac.ssu.ssutoday.core.port.VerifyPhotoInspectionPublisher
import kr.ac.ssu.ssutoday.core.status.StatusCode
import kr.ac.ssu.ssutoday.core.turnstile.TurnstileAction
import kr.ac.ssu.ssutoday.core.turnstile.TurnstileVerification
import kr.ac.ssu.ssutoday.domain.reservation.ReservationRiskPolicy
import kr.ac.ssu.ssutoday.domain.reservation.ReservationService
import kr.ac.ssu.ssutoday.domain.reservation.ReservationView
import kr.ac.ssu.ssutoday.domain.reservation.VerifyPhotoService
import kr.ac.ssu.ssutoday.domain.reservation.VerifyPhotoSignal
import kr.ac.ssu.ssutoday.domain.reservation.VerifyPhotoSignalRepository
import kr.ac.ssu.ssutoday.domain.reservation.VerifyPhotoSignalService
import kr.ac.ssu.ssutoday.domain.room.RoomService
import kr.ac.ssu.ssutoday.domain.student.AttestChallengeService
import kr.ac.ssu.ssutoday.domain.student.DeviceAttestationService
import kr.ac.ssu.ssutoday.domain.student.StudentService
import kr.ac.ssu.ssutoday.domain.student.StudentView
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.io.InputStream
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VerifyPhotoUploadAttestationTest {
    private val reservations = mock(ReservationService::class.java)
    private val photos = mock(VerifyPhotoService::class.java)
    private val students = mock(StudentService::class.java)
    private val rooms = mock(RoomService::class.java)
    private val storage = mock(FileStoragePort::class.java)
    private val tokens = mock(TokenPort::class.java)
    private val turnstile = mock(TurnstileVerificationPort::class.java)
    private val discord = mock(DiscordVerifyPhotoNotificationPort::class.java)
    private val inspections = mock(VerifyPhotoInspectionPublisher::class.java)
    private val challenges = mock(AttestChallengeService::class.java)
    private val provider = mock(PlayIntegrityVerificationPort::class.java)
    private val signalRepository = mock(VerifyPhotoSignalRepository::class.java)
    private val bytes = "abc".toByteArray()
    private val scope = AttestationChallengeScope(20260000, AttestationPurpose.VERIFY_PHOTO_UPLOAD, 42)
    private val evidence = AttestationEvidence("android", CHALLENGE, "opaque-token")
    private val reservation =
        ReservationView(42, 20260000, "1", LocalDate.of(2026, 9, 13), 0, 1, Timestamp(0), null, null, true, "admin", 0)

    init {
        `when`(turnstile.verify("turnstile", TurnstileAction.VERIFY_PHOTO_UPLOAD)).thenReturn(TurnstileVerification(Instant.EPOCH))
        `when`(reservations.getForPhotoUpload(20260000, 42)).thenReturn(reservation)
        `when`(students.get(20260000)).thenReturn(StudentView(20260000, "student", "cse", false))
        `when`(tokens.randomToken(20)).thenReturn("photo-token")
        `when`(provider.verify("opaque-token", REQUEST_HASH)).thenReturn(PlayIntegrityVerification(AttestationVerdict.VERIFIED))
        doAnswer { invocation ->
            assertEquals(bytes.size.toLong(), invocation.getArgument<Long>(3))
            assertContentEquals(bytes, invocation.getArgument<InputStream>(4).readBytes())
            "https://storage/photo.jpeg"
        }.`when`(storage).upload(anyString(), anyString(), anyString(), anyLong(), anyInput())
    }

    @Test
    fun `실제 사진 바이트를 검증하고 challenge를 소모한 다음 같은 바이트를 저장한다`() {
        assertEquals("https://storage/photo.jpeg", service(true).upload(command()))
        val order = inOrder(turnstile, reservations, provider, challenges, storage, photos, signalRepository)
        order.verify(turnstile).verify("turnstile", TurnstileAction.VERIFY_PHOTO_UPLOAD)
        order.verify(reservations).getForPhotoUpload(20260000, 42)
        order.verify(provider).verify("opaque-token", REQUEST_HASH)
        order.verify(challenges).consume(CHALLENGE, scope)
        order.verify(storage).upload(anyString(), anyString(), anyString(), anyLong(), anyInput())
        order.verify(photos).create(42, "https://storage/photo.jpeg")
        order.verify(signalRepository).save(any(VerifyPhotoSignal::class.java))
    }

    @Test
    fun `업로드마다 검토 신호를 저장하고 해당하는 신호를 모두 남긴다`() {
        val signals = PlayIntegritySignals(appsDetected = listOf("UNKNOWN_CONTROLLING"), playProtectVerdict = "HIGH_RISK")
        `when`(provider.verify("opaque-token", REQUEST_HASH)).thenReturn(PlayIntegrityVerification(AttestationVerdict.VERIFIED, signals))
        val before = Instant.now()
        service(true).upload(command())
        val after = Instant.now()

        val saved = ArgumentCaptor.forClass(VerifyPhotoSignal::class.java)
        verify(signalRepository).save(saved.capture())
        with(saved.value) {
            assertEquals(42L, reservationId)
            assertEquals(20260000, studentId)
            assertTrue(receivedAt.toInstant() in before..after)
            assertEquals(Instant.EPOCH, turnstileSolvedAt.toInstant())
            assertEquals("android", platform)
            assertEquals("VERIFIED", verdict)
            assertEquals("https://storage/photo.jpeg", photoUrl)
            assertEquals("UNKNOWN_CONTROLLING", appsDetected)
            assertEquals("HIGH_RISK", playProtectVerdict)
            assertEquals("ACCESS_RISK,PLAY_PROTECT_RISK", flags)
        }
    }

    @Test
    fun `강제 모드의 증명 누락과 거부는 스토리지 DB와 외부 발행 전에 종료한다`() {
        val service = service(true)
        assertEquals(
            StatusCode.SSU4206,
            assertFailsWith<BusinessException> { service.upload(command(AttestationEvidence())) }.status,
        )
        `when`(provider.verify("opaque-token", REQUEST_HASH)).thenReturn(PlayIntegrityVerification(AttestationVerdict.DEVICE_UNTRUSTED))
        assertEquals(StatusCode.SSU4206, assertFailsWith<BusinessException> { service.upload(command()) }.status)
        verifyNoInteractions(storage, photos, signalRepository, discord, inspections, challenges, tokens)
    }

    @Test
    fun `Turnstile과 예약 정책을 통과해야 Google 검증을 호출한다`() {
        `when`(turnstile.verify("turnstile", TurnstileAction.VERIFY_PHOTO_UPLOAD)).thenReturn(null)
        assertEquals(StatusCode.SSU4205, assertFailsWith<BusinessException> { service(true).upload(command()) }.status)
        verifyNoInteractions(reservations, provider, storage, challenges, signalRepository)

        `when`(turnstile.verify("turnstile", TurnstileAction.VERIFY_PHOTO_UPLOAD)).thenReturn(TurnstileVerification(Instant.EPOCH))
        `when`(reservations.getForPhotoUpload(20260000, 42)).thenThrow(BusinessException(StatusCode.SSU4200))
        assertEquals(StatusCode.SSU4200, assertFailsWith<BusinessException> { service(true).upload(command()) }.status)
        verifyNoInteractions(provider, storage, challenges)
    }

    @Test
    fun `관찰 모드 누락과 실패는 업로드하고 커밋 이후 알림에 판정을 남긴다`() {
        val notices = mutableListOf<String>()
        doAnswer { invocation -> notices += invocation.getArgument<String>(0) }
            .`when`(discord)
            .send(anyString(), anyLong(), anyString(), anyString(), anyString(), anyString(), anyString())
        TransactionSynchronizationManager.initSynchronization()
        try {
            service(false).upload(command(AttestationEvidence()))
            `when`(
                provider.verify("opaque-token", REQUEST_HASH),
            ).thenReturn(PlayIntegrityVerification(AttestationVerdict.PROVIDER_UNAVAILABLE))
            service(false).upload(command())
            assertTrue(notices.isEmpty())
            verifyNoInteractions(inspections)
            TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }
            assertTrue(notices[0].contains("MISSING"))
            assertTrue(notices[1].contains("PROVIDER_UNAVAILABLE"))
            assertTrue(notices.all { "관찰 모드" in it && "opaque-token" !in it && CHALLENGE !in it })
        } finally {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    @Test
    fun `인증샷 알림에 해당하는 검토 신호를 모두 붙이고 없으면 생략한다`() {
        val notices = mutableListOf<String>()
        doAnswer { invocation -> notices += invocation.getArgument<String>(0) }
            .`when`(discord)
            .send(anyString(), anyLong(), anyString(), anyString(), anyString(), anyString(), anyString())
        TransactionSynchronizationManager.initSynchronization()
        try {
            service(true).upload(command())
            `when`(provider.verify("opaque-token", REQUEST_HASH)).thenReturn(
                PlayIntegrityVerification(
                    AttestationVerdict.VERIFIED,
                    PlayIntegritySignals(appsDetected = listOf("UNKNOWN_CONTROLLING"), playProtectVerdict = "HIGH_RISK"),
                ),
            )
            service(true).upload(command())
            TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }
            assertFalse("검토 신호" in notices[0])
            assertTrue(notices[1].endsWith("\n검토 신호: ACCESS_RISK, PLAY_PROTECT_RISK"), notices[1])
        } finally {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    @Test
    fun `DB 롤백이면 관찰 알림과 후속 검사를 발행하지 않는다`() {
        TransactionSynchronizationManager.initSynchronization()
        try {
            service(false).upload(command(AttestationEvidence()))
            TransactionSynchronizationManager.getSynchronizations().forEach {
                it.afterCompletion(
                    TransactionSynchronization.STATUS_ROLLED_BACK,
                )
            }
            verifyNoInteractions(discord, inspections)
        } finally {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    private fun command(attestation: AttestationEvidence = evidence) =
        UploadPhotoCommand("turnstile", 20260000, 42, "image/jpeg", 999, bytes.inputStream(), attestation)

    private fun anyInput(): InputStream = any<InputStream>() ?: InputStream.nullInputStream()

    private fun service(enforce: Boolean) =
        VerifyPhotoApplicationService(
            reservations,
            photos,
            students,
            rooms,
            mock(ReservationCommandApplicationService::class.java),
            storage,
            tokens,
            turnstile,
            discord,
            mock(DiscordReservationActionNotificationPort::class.java),
            inspections,
            mock(VerifyPhotoInspectionPort::class.java),
            AttestationVerificationApplicationService(
                provider,
                challenges,
                enforce,
                mock(AppAttestVerificationPort::class.java),
                mock(DeviceAttestationService::class.java),
                true,
            ),
            ReservationRiskPolicy(),
            VerifyPhotoSignalService(signalRepository),
            "bucket",
            "",
            false,
            0.9,
        )

    private companion object {
        const val CHALLENGE = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8"
        const val REQUEST_HASH = "bF7nJGb7o2lT2ogovdDp0VPtyO_xoHxNgqb3OaAcDaU"
    }
}
