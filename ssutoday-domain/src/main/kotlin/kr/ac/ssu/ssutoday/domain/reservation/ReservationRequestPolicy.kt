package kr.ac.ssu.ssutoday.domain.reservation

import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class ReservationRequestPolicy {
    fun rejectionStatus(
        request: ReservationRequestView,
        admin: Boolean,
        roomConflict: Boolean,
        reservedBlocks: Int,
        studentConflict: Boolean,
        now: LocalDateTime,
    ): ReservationRequestStatus? {
        val reservationDate = request.date
        val startAt = reservationDate.atStartOfDay().plusMinutes(request.startBlock * 30L)
        val endAt = reservationDate.atStartOfDay().plusMinutes((request.endBlock + 1) * 30L)

        if (reservationDate.isBefore(now.toLocalDate())) return ReservationRequestStatus.DATE_PASSED
        if (!endAt.isAfter(now)) return ReservationRequestStatus.TIME_PASSED
        if (startAt.plusMinutes(15).isBefore(now)) return ReservationRequestStatus.TIME_PASSED
        if (!admin && startAt.minusHours(4).isAfter(now)) return ReservationRequestStatus.TOO_EARLY
        if (roomConflict) return ReservationRequestStatus.ROOM_CONFLICT

        val requestedBlocks = request.endBlock - request.startBlock + 1
        if (!admin && reservedBlocks + requestedBlocks > MAX_DAILY_BLOCKS) {
            return ReservationRequestStatus.DAILY_LIMIT_EXCEEDED
        }
        if (!admin && studentConflict) return ReservationRequestStatus.STUDENT_CONFLICT
        return null
    }

    private companion object {
        const val MAX_DAILY_BLOCKS = 6
    }
}
