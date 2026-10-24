package kr.ac.ssu.ssutoday.domain.reservation

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReservationRequestPolicyTest {
    private val policy = ReservationRequestPolicy()
    private val now = LocalDateTime.of(2026, 6, 22, 12, 0)

    @Test
    fun `기존 예약 요청 상태 코드 순서를 유지한다`() {
        assertEquals(
            ReservationRequestStatus.DATE_PASSED,
            policy.rejectionStatus(request("2026-06-21", 24, 25), false, false, 0, false, 6, now),
        )
        assertEquals(
            ReservationRequestStatus.ROOM_CONFLICT,
            policy.rejectionStatus(request("2026-06-22", 24, 25), false, true, 0, true, 6, now),
        )
        assertEquals(
            ReservationRequestStatus.DAILY_LIMIT_EXCEEDED,
            policy.rejectionStatus(request("2026-06-22", 24, 25), false, false, 5, true, 6, now),
        )
        assertEquals(
            ReservationRequestStatus.STUDENT_CONFLICT,
            policy.rejectionStatus(request("2026-06-22", 24, 25), false, false, 0, true, 6, now),
        )
    }

    @Test
    fun `관리자는 예약 가능 시점과 일일 제한 및 학생 충돌 검사를 우회한다`() {
        assertNull(
            policy.rejectionStatus(request("2026-06-22", 34, 35), true, false, 6, true, 6, now),
        )
        assertNull(
            policy.rejectionStatus(request("2026-06-24", 0, 1), true, false, 0, false, 6, now),
        )
    }

    @Test
    fun `오늘 날짜의 늦은 시간대는 4시간 전 제한 없이 예약할 수 있다`() {
        assertNull(
            policy.rejectionStatus(request("2026-06-22", 36, 37), false, false, 0, false, 6, now),
        )
    }

    @Test
    fun `예약은 전날 20시에 열린다`() {
        assertEquals(LocalDateTime.of(2026, 6, 22, 20, 0), policy.openAt(LocalDate.of(2026, 6, 23)))
    }

    @Test
    fun `내일 날짜는 전날 20시 이전에는 예약할 수 없다`() {
        assertEquals(
            ReservationRequestStatus.TOO_EARLY,
            policy.rejectionStatus(request("2026-06-23", 0, 1), false, false, 0, false, 6, now),
        )
    }

    @Test
    fun `내일 날짜는 전날 20시부터 예약할 수 있다`() {
        val twentyToday = LocalDateTime.of(2026, 6, 22, 20, 0)
        assertNull(
            policy.rejectionStatus(request("2026-06-23", 0, 1, twentyToday), false, false, 0, false, 6, twentyToday),
        )
    }

    @Test
    fun `오픈 전에 접수한 요청은 오픈 이후에 처리해도 거부한다`() {
        val received = LocalDateTime.of(2026, 6, 22, 19, 59, 59, 900_000_000)
        val processed = LocalDateTime.of(2026, 6, 22, 20, 0, 1)
        assertEquals(
            ReservationRequestStatus.TOO_EARLY,
            policy.rejectionStatus(request("2026-06-23", 0, 1, received), false, false, 0, false, 6, processed),
        )
    }

    @Test
    fun `페널티로 줄어든 하루 최대 예약 시간을 넘으면 거부한다`() {
        assertNull(policy.rejectionStatus(request("2026-06-22", 24, 25), false, false, 2, false, 4, now))
        assertEquals(
            ReservationRequestStatus.DAILY_LIMIT_EXCEEDED,
            policy.rejectionStatus(request("2026-06-22", 24, 25), false, false, 3, false, 4, now),
        )
        assertEquals(
            ReservationRequestStatus.DAILY_LIMIT_EXCEEDED,
            policy.rejectionStatus(request("2026-06-22", 24, 26), false, false, 0, false, 2, now),
        )
    }

    @Test
    fun `예약 정지 중이면 방 충돌보다 먼저 거부하고 관리자는 우회한다`() {
        assertEquals(
            ReservationRequestStatus.SUSPENDED,
            policy.rejectionStatus(request("2026-06-22", 24, 25), false, true, 0, true, null, now),
        )
        assertNull(policy.rejectionStatus(request("2026-06-22", 24, 25), true, false, 0, false, null, now))
        assertEquals(
            ReservationRequestStatus.TOO_EARLY,
            policy.rejectionStatus(request("2026-06-23", 0, 1), false, false, 0, false, null, now),
        )
    }

    private fun request(
        date: String,
        startBlock: Int,
        endBlock: Int,
        createdAt: LocalDateTime = now,
    ) = ReservationRequestView(
        id = 1L,
        studentId = 20260000,
        roomNo = "A101",
        date = LocalDate.parse(date),
        startBlock = startBlock,
        endBlock = endBlock,
        status = 0,
        createdAt = createdAt,
    )
}
