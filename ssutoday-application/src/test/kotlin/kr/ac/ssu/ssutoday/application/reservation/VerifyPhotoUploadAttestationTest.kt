package kr.ac.ssu.ssutoday.application.reservation

import kr.ac.ssu.ssutoday.application.attest.PhotoAttestationApplicationService
import kr.ac.ssu.ssutoday.application.attest.dto.PhotoAttestationEvidence
import kr.ac.ssu.ssutoday.application.reservation.dto.UploadPhotoCommand
import kr.ac.ssu.ssutoday.core.attestation.AttestationChallengeScope
import kr.ac.ssu.ssutoday.core.attestation.AttestationPurpose
import kr.ac.ssu.ssutoday.core.attestation.AttestationVerdict
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
import kr.ac.ssu.ssutoday.domain.reservation.ReservationService
import kr.ac.ssu.ssutoday.domain.reservation.ReservationView
import kr.ac.ssu.ssutoday.domain.reservation.VerifyPhotoService
import kr.ac.ssu.ssutoday.domain.room.RoomService
import kr.ac.ssu.ssutoday.domain.student.AttestChallengeService
import kr.ac.ssu.ssutoday.domain.student.DeviceAttestationService
import kr.ac.ssu.ssutoday.domain.student.StudentService
import kr.ac.ssu.ssutoday.domain.student.StudentView
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
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    private val bytes = "abc".toByteArray()
    private val scope = AttestationChallengeScope(20260000, AttestationPurpose.VERIFY_PHOTO_UPLOAD, 42)
    private val evidence = PhotoAttestationEvidence("android", CHALLENGE, "opaque-token")
    private val reservation =
        ReservationView(42, 20260000, "1", LocalDate.of(2026, 9, 13), 0, 1, Timestamp(0), null, null, true, "admin", 0)

    init {
        `when`(turnstile.verify("turnstile")).thenReturn(true)
        `when`(reservations.getForPhotoUpload(20260000, 42)).thenReturn(reservation)
        `when`(students.get(20260000)).thenReturn(StudentView(20260000, "student", "cse", false))
        `when`(tokens.randomToken(20)).thenReturn("photo-token")
        `when`(provider.verify("opaque-token", REQUEST_HASH)).thenReturn(AttestationVerdict.VERIFIED)
        doAnswer { invocation ->
            assertEquals(bytes.size.toLong(), invocation.getArgument<Long>(3))
            assertContentEquals(bytes, invocation.getArgument<InputStream>(4).readBytes())
            "https://storage/photo.jpeg"
        }.`when`(storage).upload(anyString(), anyString(), anyString(), anyLong(), anyInput())
    }

    @Test
    fun `실제 사진 바이트를 검증하고 challenge를 소모한 다음 같은 바이트를 저장한다`() {
        assertEquals("https://storage/photo.jpeg", service(true).upload(command()))
        val order = inOrder(turnstile, reservations, provider, challenges, storage, photos)
        order.verify(turnstile).verify("turnstile")
        order.verify(reservations).getForPhotoUpload(20260000, 42)
        order.verify(provider).verify("opaque-token", REQUEST_HASH)
        order.verify(challenges).consume(CHALLENGE, scope)
        order.verify(storage).upload(anyString(), anyString(), anyString(), anyLong(), anyInput())
        order.verify(photos).create(42, "https://storage/photo.jpeg")
    }

    @Test
    fun `강제 모드의 증명 누락과 거부는 스토리지 DB와 외부 발행 전에 종료한다`() {
        val service = service(true)
        assertEquals(
            StatusCode.SSU4206,
            assertFailsWith<BusinessException> { service.upload(command(PhotoAttestationEvidence())) }.status,
        )
        `when`(provider.verify("opaque-token", REQUEST_HASH)).thenReturn(AttestationVerdict.DEVICE_UNTRUSTED)
        assertEquals(StatusCode.SSU4206, assertFailsWith<BusinessException> { service.upload(command()) }.status)
        verifyNoInteractions(storage, photos, discord, inspections, challenges, tokens)
    }

    @Test
    fun `Turnstile과 예약 정책을 통과해야 Google 검증을 호출한다`() {
        `when`(turnstile.verify("turnstile")).thenReturn(false)
        assertEquals(StatusCode.SSU4205, assertFailsWith<BusinessException> { service(true).upload(command()) }.status)
        verifyNoInteractions(reservations, provider, storage, challenges)

        `when`(turnstile.verify("turnstile")).thenReturn(true)
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
            service(false).upload(command(PhotoAttestationEvidence()))
            `when`(provider.verify("opaque-token", REQUEST_HASH)).thenReturn(AttestationVerdict.PROVIDER_UNAVAILABLE)
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
    fun `DB 롤백이면 관찰 알림과 후속 검사를 발행하지 않는다`() {
        TransactionSynchronizationManager.initSynchronization()
        try {
            service(false).upload(command(PhotoAttestationEvidence()))
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

    private fun command(attestation: PhotoAttestationEvidence = evidence) =
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
            PhotoAttestationApplicationService(
                provider,
                challenges,
                enforce,
                mock(AppAttestVerificationPort::class.java),
                mock(DeviceAttestationService::class.java),
                true,
            ),
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
