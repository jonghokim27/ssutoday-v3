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

    private fun request(date: String, startBlock: Int, endBlock: Int) = ReservationRequestView(
        id = 1L,
        studentId = 20260000,
        roomNo = "A101",
        date = LocalDate.parse(date),
        startBlock = startBlock,
        endBlock = endBlock,
        status = 0,
    )
}
