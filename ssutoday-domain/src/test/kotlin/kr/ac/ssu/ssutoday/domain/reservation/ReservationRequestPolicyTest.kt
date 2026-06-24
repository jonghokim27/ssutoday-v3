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
            policy.rejectionStatus(request("2026-06-21", 24, 25), false, false, 0, false, now),
        )
        assertEquals(
            ReservationRequestStatus.ROOM_CONFLICT,
            policy.rejectionStatus(request("2026-06-22", 24, 25), false, true, 0, true, now),
        )
        assertEquals(
            ReservationRequestStatus.DAILY_LIMIT_EXCEEDED,
            policy.rejectionStatus(request("2026-06-22", 24, 25), false, false, 5, true, now),
        )
        assertEquals(
            ReservationRequestStatus.STUDENT_CONFLICT,
            policy.rejectionStatus(request("2026-06-22", 24, 25), false, false, 0, true, now),
        )
    }

    @Test
    fun `관리자는 예약 가능 시점과 일일 제한 및 학생 충돌 검사를 우회한다`() {
        assertNull(
            policy.rejectionStatus(request("2026-06-22", 34, 35), true, false, 6, true, now),
        )
    }

    @Test
    fun `오늘 날짜의 늦은 시간대는 4시간 전 제한 없이 예약할 수 있다`() {
        assertNull(
            policy.rejectionStatus(request("2026-06-22", 36, 37), false, false, 0, false, now),
        )
    }

    @Test
    fun `내일 날짜는 전날 20시 이전에는 예약할 수 없다`() {
        assertEquals(
            ReservationRequestStatus.TOO_EARLY,
            policy.rejectionStatus(request("2026-06-23", 0, 1), false, false, 0, false, now),
        )
    }

    @Test
    fun `내일 날짜는 전날 20시부터 예약할 수 있다`() {
        val twentyToday = LocalDateTime.of(2026, 6, 22, 20, 0)
        assertNull(
            policy.rejectionStatus(request("2026-06-23", 0, 1), false, false, 0, false, twentyToday),
        )
    }

    private fun request(
        date: String,
        startBlock: Int,
        endBlock: Int,
    ) = ReservationRequestView(
        id = 1L,
        studentId = 20260000,
        roomNo = "A101",
        date = LocalDate.parse(date),
        startBlock = startBlock,
        endBlock = endBlock,
        status = 0,
    )
}
