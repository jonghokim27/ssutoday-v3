package kr.ac.ssu.ssutoday.domain.reservation

import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime

@Component
class ReservationRequestPolicy {
    /** 해당 날짜의 예약이 열리는 시각. 전날 20:00이다. */
    fun openAt(date: LocalDate): LocalDateTime = date.atStartOfDay().minusHours(OPEN_HOURS_BEFORE)

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
        // 처리 시각이 아니라 접수 시각으로 판단해야 오픈 직전 요청이 처리 지연 덕분에 통과하지 않는다.
        if (!admin && request.createdAt.isBefore(openAt(reservationDate))) return ReservationRequestStatus.TOO_EARLY
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
        const val OPEN_HOURS_BEFORE = 4L
    }
}
