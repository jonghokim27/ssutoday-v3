package kr.ac.ssu.ssutoday.application.reservation

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object ReservationDateTimeFormatter {
    private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy년 MM월 dd일")
    private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun format(
        date: LocalDate,
        startBlock: Int,
        endBlock: Int,
    ): String {
        val dateText = date.format(DATE_FORMATTER)
        val day = koreanDayOfWeek(date.dayOfWeek)
        val startTime = blockToTime(startBlock)
        val endTime = blockToTime(endBlock + 1)

        return "$dateText($day) $startTime ~ $endTime"
    }

    private fun blockToTime(block: Int): String =
        LocalTime.MIDNIGHT
            .plusMinutes(block * 30L)
            .format(TIME_FORMATTER)

    private fun koreanDayOfWeek(dayOfWeek: DayOfWeek): String =
        when (dayOfWeek) {
            DayOfWeek.MONDAY -> "월"
            DayOfWeek.TUESDAY -> "화"
            DayOfWeek.WEDNESDAY -> "수"
            DayOfWeek.THURSDAY -> "목"
            DayOfWeek.FRIDAY -> "금"
            DayOfWeek.SATURDAY -> "토"
            DayOfWeek.SUNDAY -> "일"
        }
}
