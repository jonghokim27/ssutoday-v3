package kr.ac.ssu.ssutoday.application.reservation

import kr.ac.ssu.ssutoday.application.anyOf
import kr.ac.ssu.ssutoday.application.attest.AttestationVerificationApplicationService
import kr.ac.ssu.ssutoday.application.captured
import kr.ac.ssu.ssutoday.application.penalty.PenaltyCommandApplicationService
import kr.ac.ssu.ssutoday.core.dto.PushMessage
import kr.ac.ssu.ssutoday.core.dto.PushMessages
import kr.ac.ssu.ssutoday.core.penalty.PenaltyReason
import kr.ac.ssu.ssutoday.core.port.DiscordAttestationNotificationPort
import kr.ac.ssu.ssutoday.core.port.DiscordReservationActionNotificationPort
import kr.ac.ssu.ssutoday.core.port.PushMessagePublisher
import kr.ac.ssu.ssutoday.core.port.ReservationRequestPublisher
import kr.ac.ssu.ssutoday.core.port.TurnstileVerificationPort
import kr.ac.ssu.ssutoday.domain.config.ConfigService
import kr.ac.ssu.ssutoday.domain.penalty.PenaltyService
import kr.ac.ssu.ssutoday.domain.reservation.ReservationCompletionPolicy
import kr.ac.ssu.ssutoday.domain.reservation.ReservationRequestPolicy
import kr.ac.ssu.ssutoday.domain.reservation.ReservationRequestService
import kr.ac.ssu.ssutoday.domain.reservation.ReservationRequestSignalService
import kr.ac.ssu.ssutoday.domain.reservation.ReservationRiskPolicy
import kr.ac.ssu.ssutoday.domain.reservation.ReservationService
import kr.ac.ssu.ssutoday.domain.reservation.ReservationView
import kr.ac.ssu.ssutoday.domain.reservation.VerifyPhotoService
import kr.ac.ssu.ssutoday.domain.room.RoomService
import kr.ac.ssu.ssutoday.domain.room.RoomView
import kr.ac.ssu.ssutoday.domain.student.DeviceService
import kr.ac.ssu.ssutoday.domain.student.StudentService
import kr.ac.ssu.ssutoday.domain.student.StudentView
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ReservationPenaltyCancelTest {
    private val reservations = mock(ReservationService::class.java)
    private val penalties = mock(PenaltyCommandApplicationService::class.java)
    private val publisher = mock(PushMessagePublisher::class.java)
    private val devices = mock(DeviceService::class.java)
    private val rooms = mock(RoomService::class.java)
    private val students = mock(StudentService::class.java)
    private val now = LocalDateTime.now()

    // 오늘 0시에 시작해 23시 30분에 끝나는 예약이라 실행 시각과 관계없이 촬영 기한이 지나 있다.
    private val reservation =
        ReservationView(
            id = 7,
            studentId = 20260000,
            roomNo = "1",
            date = LocalDate.now(),
            startBlock = 0,
            endBlock = 47,
            createdAt = Timestamp.valueOf(now.toLocalDate().atStartOfDay().minusDays(1)),
            deletedAt = null,
            deletedReason = null,
            active = true,
            adminToken = "token",
            photoDeleteCount = 1,
        )

    init {
        `when`(devices.findReservationPushTokens(20260000)).thenReturn(listOf("device-token"))
        `when`(rooms.getByNo("1")).thenReturn(RoomView("1", "스터디룸 1", "cse", 4, "", "", "", "", true))
        `when`(students.get(20260000)).thenReturn(StudentView(20260000, "홍길동", "cse", false))
        `when`(reservations.findMissingPhotoReservations(anyOf(LocalDate::class.java), anyInt())).thenReturn(listOf(reservation))
        `when`(reservations.find(7)).thenReturn(reservation)
        `when`(reservations.findByAdminToken("token")).thenReturn(reservation)
    }

    @BeforeTest
    fun begin() = TransactionSynchronizationManager.initSynchronization()

    @AfterTest
    fun end() = TransactionSynchronizationManager.clearSynchronization()

    @Test
    fun `인증샷 미촬영 자동 취소에 페널티를 부여하면 취소 알림 한 건에 함께 안내한다`() {
        `when`(penalties.grantAutomatically(reservation, PenaltyReason.PHOTO_MISSING)).thenReturn(true)
        service().cancelMissingPhotos()
        commit()

        val push = push()
        assertEquals(PushMessages.autoCancelTitle("스터디룸 1"), push.title)
        assertEquals(PushMessages.AUTO_CANCEL_PENALTY_BODY, push.body)
        assertEquals("penalty", push.category)
    }

    @Test
    fun `시행일 전이라 페널티를 부여하지 않으면 기존 취소 알림을 보낸다`() {
        `when`(penalties.grantAutomatically(reservation, PenaltyReason.PHOTO_MISSING)).thenReturn(false)
        service().cancelMissingPhotos()
        commit()

        val push = push()
        assertEquals(PushMessages.AUTO_CANCEL_BODY, push.body)
        assertEquals("reservation", push.category)
    }

    @Test
    fun `자동 검사의 두 번째 거부로 취소하면 인증샷 미인정 페널티를 함께 안내한다`() {
        `when`(penalties.grantAutomatically(reservation, PenaltyReason.PHOTO_REJECTED)).thenReturn(true)
        val result = service().rejectVerifyPhotoByInspection(7)
        commit()

        assertEquals(3, result.status)
        verify(reservations).cancelByAdmin(7, "관리자 취소 (사유: 인증샷으로 입실하였음을 확인할 수 없음 / 처리자: 시스템)")
        val push = push()
        assertEquals(PushMessages.adminCancelTitle("스터디룸 1"), push.title)
        assertEquals(PushMessages.INSPECTION_CANCEL_PENALTY_BODY, push.body)
        assertEquals("penalty", push.category)
    }

    @Test
    fun `관리자가 직접 취소하면 페널티 없이 기존 알림을 보낸다`() {
        service().executeAdminActionByToken("token", "reserveCancel", "이용 규칙 위반", "김관리")
        commit()

        val push = push()
        assertEquals(PushMessages.adminCancelBody("이용 규칙 위반"), push.body)
        assertEquals("reservation", push.category)
    }

    private fun commit() = TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }

    private fun push(): PushMessage {
        val captor: ArgumentCaptor<PushMessage> = ArgumentCaptor.forClass(PushMessage::class.java)
        verify(publisher).publish(captor.captured())
        return captor.value
    }

    private fun service() =
        ReservationCommandApplicationService(
            mock(ReservationRequestService::class.java),
            reservations,
            mock(VerifyPhotoService::class.java),
            rooms,
            students,
            devices,
            mock(ConfigService::class.java),
            ReservationRequestPolicy(),
            mock(ReservationCompletionPolicy::class.java),
            mock(ReservationRequestPublisher::class.java),
            publisher,
            mock(TurnstileVerificationPort::class.java),
            mock(DiscordReservationActionNotificationPort::class.java),
            mock(AttestationVerificationApplicationService::class.java),
            mock(DiscordAttestationNotificationPort::class.java),
            mock(ReservationRequestSignalService::class.java),
            ReservationRiskPolicy(),
            mock(PenaltyService::class.java),
            penalties,
        )
}
