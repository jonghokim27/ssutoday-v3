package kr.ac.ssu.ssutoday.domain.reservation

import kr.ac.ssu.ssutoday.core.exception.BusinessException
import kr.ac.ssu.ssutoday.core.status.StatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReservationPolicyTest {
    private val policy = ReservationPolicy()

    @Test
    fun `예약은 최대 네 블록까지만 가능하다`() {
        val exception =
            assertFailsWith<BusinessException> {
                policy.validateBlocks(12, 16)
            }

        assertEquals(StatusCode.SSU4000, exception.status)
    }

    @Test
    fun `관리자는 최대 네 블록 제한을 우회한다`() {
        policy.validateBlocks(12, 16, admin = true)
    }
}
