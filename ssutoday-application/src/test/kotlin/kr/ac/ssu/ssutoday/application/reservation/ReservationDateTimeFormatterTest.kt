package kr.ac.ssu.ssutoday.application.reservation

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class ReservationDateTimeFormatterTest {
    @Test
    fun `월과 일은 앱 화면처럼 두 자리로 맞춘다`() {
        assertEquals("2026년 11월 03일(화) 10:00 ~ 12:00", ReservationDateTimeFormatter.format(LocalDate.of(2026, 11, 3), 20, 23))
        assertEquals("2027년 02월 08일(월)", ReservationDateTimeFormatter.formatDate(LocalDate.of(2027, 2, 8)))
        assertEquals("2027년 02월 08일(월) 09:05", ReservationDateTimeFormatter.formatDateTime(LocalDateTime.of(2027, 2, 8, 9, 5)))
    }
}
