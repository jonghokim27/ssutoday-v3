package kr.ac.ssu.ssutoday.application.penalty

import kr.ac.ssu.ssutoday.application.reservation.ReservationDateTimeFormatter
import kr.ac.ssu.ssutoday.application.reservation.StudentInfoFormatter
import kr.ac.ssu.ssutoday.domain.penalty.PenaltyStanding
import kr.ac.ssu.ssutoday.domain.reservation.ReservationView
import kr.ac.ssu.ssutoday.domain.student.StudentView

/** 관리자 화면과 Discord 알림에 쓰는 페널티 표기. */
object PenaltyTextFormatter {
    /** 예: 2회 (1일 최대 예약 시간 2시간), 예약 정지 (2027년 02월 08일(월) 15:12까지) */
    fun standing(standing: PenaltyStanding): String {
        val suspendedUntil = standing.suspendedUntil
        if (suspendedUntil != null) return "예약 정지 (${ReservationDateTimeFormatter.formatDateTime(suspendedUntil)}까지)"
        return "${standing.photoCount}회 (1일 최대 예약 시간 ${hours(requireNotNull(standing.maxDailyBlocks))})"
    }

    fun hours(blocks: Int): String {
        val minutes = blocks * 30
        return if (minutes % 60 == 0) "${minutes / 60}시간" else "${minutes / 60}시간 30분"
    }

    fun reservationFields(
        reservation: ReservationView,
        student: StudentView,
        roomName: String,
    ): List<Pair<String, String>> =
        listOf(
            "예약 고유번호" to reservation.id.toString(),
            "예약자" to StudentInfoFormatter.format(student),
            "시설명" to roomName,
            "예약 날짜 및 시간" to ReservationDateTimeFormatter.format(reservation.date, reservation.startBlock, reservation.endBlock),
        )
}
