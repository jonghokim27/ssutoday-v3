package kr.ac.ssu.ssutoday.domain.reservation

import kr.ac.ssu.ssutoday.core.exception.BusinessException
import kr.ac.ssu.ssutoday.core.status.StatusCode
import org.springframework.stereotype.Component

@Component
class ReservationPolicy {
    fun validateBlocks(
        startBlock: Int,
        endBlock: Int,
        admin: Boolean = false,
    ) {
        if (startBlock > endBlock) throw BusinessException(StatusCode.SSU4000)
        if (!admin && endBlock - startBlock > 3) throw BusinessException(StatusCode.SSU4000)
        if (startBlock !in 12..43 || endBlock !in 12..43) {
            throw BusinessException(StatusCode.SSU4000)
        }
    }
}
