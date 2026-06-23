package kr.ac.ssu.ssutoday.domain.reservation

import kr.ac.ssu.ssutoday.core.exception.BusinessException
import kr.ac.ssu.ssutoday.core.status.StatusCode
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReservationCompletionPolicyTest {
    private val policy = ReservationCompletionPolicy()

    @Test
    fun `인증샷이 없으면 기존 SSU4235를 반환한다`() {
        val exception =
            assertFailsWith<BusinessException> {
                policy.completionBlock(
                    reservation(endBlock = 29),
                    verifyPhotoSatisfied = false,
                    now = LocalDateTime.of(2026, 6, 22, 14, 40),
                )
            }

        assertEquals(StatusCode.SSU4235, exception.status)
    }

    @Test
    fun `종료 가능 시각을 기존 30분 블록 규칙으로 계산한다`() {
        assertEquals(
            27,
            policy.completionBlock(
                reservation(endBlock = 30),
                verifyPhotoSatisfied = true,
                now = LocalDateTime.of(2026, 6, 22, 14, 10),
            ),
        )
    }

    private fun reservation(endBlock: Int) =
        ReservationView(
            id = 1L,
            studentId = 20260000,
            roomNo = "A101",
            date = LocalDate.parse("2026-06-22"),
            startBlock = 24,
            endBlock = endBlock,
            createdAt = Timestamp.valueOf("2026-06-22 12:00:00"),
            deletedAt = null,
            deletedReason = null,
            active = true,
        )
}
