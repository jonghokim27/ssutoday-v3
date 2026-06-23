package kr.ac.ssu.ssutoday.domain.reservation

enum class ReservationRequestStatus(
    val code: Int,
) {
    PENDING(0),
    ACCEPTED(1),
    DATE_PASSED(2),
    TIME_PASSED(3),
    TOO_EARLY(4),
    ROOM_CONFLICT(5),
    DAILY_LIMIT_EXCEEDED(6),
    STUDENT_CONFLICT(7),
}
