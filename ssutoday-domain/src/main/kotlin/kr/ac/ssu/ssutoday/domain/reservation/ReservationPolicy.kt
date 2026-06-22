package kr.ac.ssu.ssutoday.domain.reservation

import kr.ac.ssu.ssutoday.core.exception.BusinessException
import kr.ac.ssu.ssutoday.core.status.SsuStatus
import org.springframework.stereotype.Component
@Component
class ReservationPolicy {
    fun validateBlocks(startBlock: Int, endBlock: Int) {
        if (startBlock > endBlock) throw BusinessException(SsuStatus.SSU4000)
        if (endBlock - startBlock > 3) throw BusinessException(SsuStatus.SSU4000)
        if (startBlock !in 12..43 || endBlock !in 12..43) {
            throw BusinessException(SsuStatus.SSU4000)
        }
    }

}
