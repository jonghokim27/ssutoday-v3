package kr.ac.ssu.ssutoday.domain.reservation

import kr.ac.ssu.ssutoday.core.exception.BusinessException
import kr.ac.ssu.ssutoday.core.status.StatusCode
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class ReservationCompletionPolicy {
    fun completionBlock(
        reservation: ReservationView,
        verifyPhotoSatisfied: Boolean,
        now: LocalDateTime,
    ): Int {
        val reservationDate = reservation.date
        val startAt = reservationDate.atStartOfDay().plusMinutes(reservation.startBlock * 30L)
        val endAt = reservationDate.atStartOfDay().plusMinutes((reservation.endBlock + 1) * 30L)

        if (reservationDate.isBefore(now.toLocalDate())) throw BusinessException(StatusCode.SSU4231)
        if (now.isAfter(endAt)) throw BusinessException(StatusCode.SSU4232)
        if (now.isBefore(startAt)) throw BusinessException(StatusCode.SSU4233)
        if (now.isBefore(startAt.plusMinutes(30))) throw BusinessException(StatusCode.SSU4234)
        if (!verifyPhotoSatisfied) throw BusinessException(StatusCode.SSU4235)

        val currentBlock = (now.hour * 60 + now.minute) / 30
        val completionBlock =
            if (now.minute in 0..20 || now.minute in 30..50) {
                currentBlock - 1
            } else {
                currentBlock
            }
        if (reservation.endBlock == completionBlock) throw BusinessException(StatusCode.SSU4236)
        return completionBlock
    }
}
