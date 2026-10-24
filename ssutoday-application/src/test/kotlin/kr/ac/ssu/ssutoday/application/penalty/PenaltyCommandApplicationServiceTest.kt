package kr.ac.ssu.ssutoday.application.penalty

import kr.ac.ssu.ssutoday.application.anyOf
import kr.ac.ssu.ssutoday.application.captured
import kr.ac.ssu.ssutoday.application.penalty.dto.GrantPenaltyCommand
import kr.ac.ssu.ssutoday.application.penalty.dto.PenaltyActionOutcome
import kr.ac.ssu.ssutoday.application.penalty.dto.RevokePenaltyCommand
import kr.ac.ssu.ssutoday.core.dto.PushMessage
import kr.ac.ssu.ssutoday.core.dto.PushMessages
import kr.ac.ssu.ssutoday.core.penalty.PenaltyReason
import kr.ac.ssu.ssutoday.core.port.DiscordPenaltyNotificationPort
import kr.ac.ssu.ssutoday.core.port.PushMessagePublisher
import kr.ac.ssu.ssutoday.domain.penalty.Penalty
import kr.ac.ssu.ssutoday.domain.penalty.PenaltyPolicy
import kr.ac.ssu.ssutoday.domain.penalty.PenaltyRepository
import kr.ac.ssu.ssutoday.domain.penalty.PenaltyService
import kr.ac.ssu.ssutoday.domain.reservation.ReservationService
import kr.ac.ssu.ssutoday.domain.reservation.ReservationView
import kr.ac.ssu.ssutoday.domain.room.RoomService
import kr.ac.ssu.ssutoday.domain.room.RoomView
import kr.ac.ssu.ssutoday.domain.student.DeviceService
import kr.ac.ssu.ssutoday.domain.student.StudentService
import kr.ac.ssu.ssutoday.domain.student.StudentView
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PenaltyCommandApplicationServiceTest {
    private val penalties = mutableListOf<Penalty>()
    private val penaltyRepository = mock(PenaltyRepository::class.java)
    private val reservations = mock(ReservationService::class.java)
    private val publisher = mock(PushMessagePublisher::class.java)
    private val discord = RecordingDiscord()
    private val today = LocalDate.now()
    private val usedYesterday = reservation(1, "used", today.minusDays(1))

    init {
        `when`(penaltyRepository.save(anyOf(Penalty::class.java))).thenAnswer { invocation ->
            (invocation.arguments[0] as Penalty).also {
                it.id = penalties.size + 1L
                penalties += it
            }
        }
        `when`(
            penaltyRepository.findAllByStudentIdAndRevokedAtIsNullAndExpiresAtAfterOrderByGrantedAtDesc(
                anyInt(),
                anyOf(Timestamp::class.java),
            ),
        ).thenAnswer { invocation ->
            val now = invocation.arguments[1] as Timestamp
            penalties.filter { it.revokedAt == null && it.expiresAt.after(now) }.sortedByDescending { it.grantedAt }
        }
        `when`(penaltyRepository.existsByReservationIdAndTypeAndRevokedAtIsNull(anyLong(), anyString())).thenAnswer { invocation ->
            penalties.any { it.reservationId == invocation.arguments[0] && it.type == invocation.arguments[1] && it.revokedAt == null }
        }
        `when`(penaltyRepository.findByAdminTokenForUpdate(anyString())).thenAnswer { invocation ->
            penalties.firstOrNull { it.adminToken == invocation.arguments[0] }
        }
        `when`(penaltyRepository.findById(anyLong())).thenAnswer { invocation ->
            Optional.ofNullable(penalties.firstOrNull { it.id == invocation.arguments[0] })
        }
        `when`(reservations.findByAdminTokenForUpdate("used")).thenReturn(usedYesterday)
    }

    @BeforeTest
    fun begin() = TransactionSynchronizationManager.initSynchronization()

    @AfterTest
    fun end() = TransactionSynchronizationManager.clearSynchronization()

    @Test
    fun `인증샷 미인정을 부여하면 커밋 후 학생 푸시와 취소 버튼이 달린 Discord 알림을 보낸다`() {
        val result = service().grantByAdmin(GrantPenaltyCommand("used", PenaltyReason.PHOTO_OUTSIDE, " 김관리 ", false))

        assertEquals(PenaltyActionOutcome.DONE, result.outcome)
        assertTrue("적용 중인 페널티" to "1회 (1일 최대 예약 시간 3시간)" in result.details)
        with(penalties.single()) {
            assertEquals("PHOTO", type)
            assertEquals("PHOTO_OUTSIDE", reason)
            assertEquals("김관리", grantedBy)
            assertEquals(1L, reservationId)
        }
        verifyNoInteractions(publisher)
        assertTrue(discord.sent.isEmpty())

        commit()
        val push = pushes().single()
        assertEquals(PushMessages.PENALTY_GRANT_TITLE, push.title)
        assertEquals(PushMessages.penaltyGrantBody(usedYesterday.date, "스터디룸 1"), push.body)
        assertEquals("reservation", push.category)
        assertEquals(PushMessages.PENALTY_PATH, push.path)
        val (content, fields, revokeToken) = discord.sent.single()
        assertEquals("**[페널티 부여 알림]**", content)
        assertEquals(penalties.single().adminToken, revokeToken)
        assertTrue("부여 사유" to "스터디룸 외부에서 촬영된 인증샷" in fields)
        assertTrue("처리자" to "김관리" in fields)
    }

    @Test
    fun `부정 예약은 해당 예약과 이용 전 예약을 취소하고 정지 기한을 알린다`() {
        val tomorrow = reservation(2, "fraud", today.plusDays(1))
        val upcoming = reservation(3, "other", today.plusDays(2))
        `when`(reservations.findByAdminTokenForUpdate("fraud")).thenReturn(tomorrow)
        `when`(reservations.findUpcoming(eq(20260000), anyOf(LocalDate::class.java), anyInt()))
            .thenReturn(listOf(tomorrow, upcoming))

        val result = service().grantByAdmin(GrantPenaltyCommand("fraud", PenaltyReason.FRAUD_AUTOMATION, "김관리", true))

        assertEquals(PenaltyActionOutcome.DONE, result.outcome)
        assertTrue("취소된 예약" to "2건" in result.details)
        verify(reservations).cancelByAdmin(2, "부정 예약 취소 (처리자: 김관리)")
        verify(reservations).cancelByAdmin(3, "부정 예약 취소 (처리자: 김관리)")
        commit()
        val push = pushes().single()
        assertEquals(PushMessages.SUSPENSION_TITLE, push.title)
        val expiresAt = penalties.single().expiresAt
        val suspendedUntil = expiresAt.toLocalDateTime().toLocalDate()
        assertEquals(PushMessages.suspensionBody(true, suspendedUntil, "매크로 등 자동화 프로그램 이용"), push.body)
    }

    @Test
    fun `이미 종료된 예약의 부정 예약은 예약을 취소하지 않고 다른 예약 취소는 선택에 따른다`() {
        val result = service().grantByAdmin(GrantPenaltyCommand("used", PenaltyReason.FRAUD_PHOTO, "김관리", false))

        assertEquals(PenaltyActionOutcome.DONE, result.outcome)
        assertTrue("취소된 예약" to "0건" in result.details)
        verify(reservations, never()).cancelByAdmin(anyLong(), anyString())
        verify(reservations, never()).findUpcoming(anyInt(), anyOf(LocalDate::class.java), anyInt())
        commit()
        assertTrue(pushes().single().body.startsWith("부정 예약이 확인되었어요."))
    }

    @Test
    fun `부여할 수 없는 요청은 저장하지 않는다`() {
        val service = service()

        assertEquals(
            PenaltyActionOutcome.NOT_FOUND,
            service.grantByAdmin(GrantPenaltyCommand("unknown", PenaltyReason.PHOTO_OUTSIDE, "김관리", false)).outcome,
        )
        assertEquals(
            PenaltyActionOutcome.INVALID_INPUT,
            service.grantByAdmin(GrantPenaltyCommand("used", PenaltyReason.PHOTO_MISSING, "김관리", false)).outcome,
        )
        assertEquals(PenaltyActionOutcome.INVALID_INPUT, service.grantByAdmin(GrantPenaltyCommand("used", null, "김관리", false)).outcome)
        assertEquals(
            PenaltyActionOutcome.INVALID_INPUT,
            service.grantByAdmin(GrantPenaltyCommand("used", PenaltyReason.PHOTO_OUTSIDE, " ", false)).outcome,
        )
        assertEquals(
            PenaltyActionOutcome.NOT_IN_EFFECT,
            service(
                startDate = today.plusDays(1),
            ).grantByAdmin(GrantPenaltyCommand("used", PenaltyReason.PHOTO_OUTSIDE, "김관리", false)).outcome,
        )
        assertTrue(penalties.isEmpty())

        service.grantByAdmin(GrantPenaltyCommand("used", PenaltyReason.PHOTO_OUTSIDE, "김관리", false))
        assertEquals(
            PenaltyActionOutcome.ALREADY_GRANTED,
            service.grantByAdmin(GrantPenaltyCommand("used", PenaltyReason.PHOTO_OUTSIDE, "김관리", false)).outcome,
        )
        assertEquals(1, penalties.size)
    }

    @Test
    fun `이용이 오래전에 끝난 예약에도 인증샷 미인정을 부여한다`() {
        val old = reservation(4, "old", today.minusDays(60))
        `when`(reservations.findByAdminTokenForUpdate("old")).thenReturn(old)

        val result = service().grantByAdmin(GrantPenaltyCommand("old", PenaltyReason.PHOTO_OUTSIDE, "김관리", false))

        assertEquals(PenaltyActionOutcome.DONE, result.outcome)
        assertEquals(4L, penalties.single().reservationId)
    }

    @Test
    fun `자동 부여는 시행일 이후 예약에 한 번만 하고 학생 푸시는 호출한 쪽에 맡긴다`() {
        assertFalse(service(startDate = today).grantAutomatically(usedYesterday, PenaltyReason.PHOTO_MISSING))
        assertTrue(service().grantAutomatically(usedYesterday, PenaltyReason.PHOTO_MISSING))
        assertFalse(service().grantAutomatically(usedYesterday, PenaltyReason.PHOTO_REJECTED))
        assertEquals("시스템", penalties.single().grantedBy)

        commit()
        verifyNoInteractions(publisher)
        val (content, _, revokeToken) = discord.sent.single()
        assertEquals("**[페널티 부여 알림]**", content)
        assertEquals(penalties.single().adminToken, revokeToken)
    }

    @Test
    fun `대상 학과가 아닌 학생에게는 자동 부여와 관리자 부여를 모두 하지 않는다`() {
        val other = reservation(5, "media", today.minusDays(1), studentId = 20269999)
        `when`(reservations.findByAdminTokenForUpdate("media")).thenReturn(other)
        val service = service()

        assertFalse(service.grantAutomatically(other, PenaltyReason.PHOTO_MISSING))
        listOf(PenaltyReason.PHOTO_OUTSIDE, PenaltyReason.FRAUD_AUTOMATION).forEach {
            assertEquals(PenaltyActionOutcome.NOT_TARGET, service.grantByAdmin(GrantPenaltyCommand("media", it, "김관리", true)).outcome)
        }
        assertTrue(penalties.isEmpty())
        verify(reservations, never()).cancelByAdmin(anyLong(), anyString())
    }

    @Test
    fun `부정 예약 페널티를 취소해 정지가 풀리면 정지 해제 알림을 보낸다`() {
        val service = service()
        service.grantByAdmin(GrantPenaltyCommand("used", PenaltyReason.FRAUD_ACCOUNT, "김관리", false))
        val token = penalties.single().adminToken
        TransactionSynchronizationManager.clearSynchronization()
        TransactionSynchronizationManager.initSynchronization()

        assertEquals(PenaltyActionOutcome.INVALID_INPUT, service.revokeByAdmin(RevokePenaltyCommand(token, " ", "김관리")).outcome)
        val result = service.revokeByAdmin(RevokePenaltyCommand(token, "이의제기 인용", "김관리"))

        assertEquals(PenaltyActionOutcome.DONE, result.outcome)
        assertTrue("적용 중인 페널티" to "0회 (1일 최대 예약 시간 3시간)" in result.details)
        assertEquals("이의제기 인용", penalties.single().revokeReason)
        commit()
        val push = pushes().single()
        assertEquals(PushMessages.SUSPENSION_LIFT_TITLE, push.title)
        assertEquals(PushMessages.suspensionLiftBody(LocalDate.now()), push.body)
        assertEquals(PenaltyActionOutcome.ALREADY_REVOKED, service.revokeByAdmin(RevokePenaltyCommand(token, "중복", "김관리")).outcome)
        assertEquals(PenaltyActionOutcome.NOT_FOUND, service.revokeByAdmin(RevokePenaltyCommand("unknown", "사유", "김관리")).outcome)
    }

    @Test
    fun `인증샷 페널티 취소는 페널티 취소 알림을 보낸다`() {
        val service = service()
        service.grantAutomatically(usedYesterday, PenaltyReason.PHOTO_MISSING)
        TransactionSynchronizationManager.clearSynchronization()
        TransactionSynchronizationManager.initSynchronization()

        service.revokeByAdmin(RevokePenaltyCommand(penalties.single().adminToken, "앱 오류 확인", "김관리"))
        commit()
        val push = pushes().single()
        assertEquals(PushMessages.PENALTY_REVOKE_TITLE, push.title)
        assertEquals(PushMessages.penaltyRevokeBody(LocalDate.now()), push.body)
    }

    private fun commit() = TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }

    private fun pushes(): List<PushMessage> {
        val captor: ArgumentCaptor<PushMessage> = ArgumentCaptor.forClass(PushMessage::class.java)
        verify(publisher, atLeastOnce()).publish(captor.captured())
        return captor.allValues
    }

    private fun service(startDate: LocalDate = LocalDate.of(2026, 1, 1)): PenaltyCommandApplicationService {
        val students = mock(StudentService::class.java)
        `when`(students.get(20260000)).thenReturn(StudentView(20260000, "홍길동", "cse", false))
        `when`(students.get(20269999)).thenReturn(StudentView(20269999, "김미디어", "media", false))
        val rooms = mock(RoomService::class.java)
        `when`(rooms.getByNo("1")).thenReturn(RoomView("1", "스터디룸 1", "cse", 4, "", "", "", "", true))
        val devices = mock(DeviceService::class.java)
        `when`(devices.findReservationPushTokens(20260000)).thenReturn(listOf("device-token"))
        val policy = PenaltyPolicy(startDate.toString(), "cse")
        return PenaltyCommandApplicationService(
            PenaltyService(penaltyRepository, policy),
            policy,
            reservations,
            students,
            rooms,
            devices,
            publisher,
            discord,
        )
    }

    private class RecordingDiscord : DiscordPenaltyNotificationPort {
        val sent = mutableListOf<Triple<String, List<Pair<String, String>>, String?>>()

        override fun send(
            content: String,
            fields: List<Pair<String, String>>,
            revokeToken: String?,
        ) {
            sent += Triple(content, fields, revokeToken)
        }
    }

    private fun reservation(
        id: Long,
        token: String,
        date: LocalDate,
        studentId: Int = 20260000,
    ) = ReservationView(
        id = id,
        studentId = studentId,
        roomNo = "1",
        date = date,
        startBlock = 20,
        endBlock = 23,
        createdAt = Timestamp.valueOf(LocalDateTime.now().minusDays(3)),
        deletedAt = null,
        deletedReason = null,
        active = true,
        adminToken = token,
        photoDeleteCount = 0,
    )
}
