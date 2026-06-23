package kr.ac.ssu.ssutoday.batch.crawler.base

import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal object ArticleDateParser {
    fun parseDateTime(
        value: String,
        formatter: DateTimeFormatter,
    ): Timestamp = Timestamp.valueOf(LocalDateTime.parse(normalize(value), formatter))

    fun parseDate(
        value: String,
        formatter: DateTimeFormatter,
    ): Timestamp = Timestamp.valueOf(LocalDate.parse(normalize(value), formatter).atStartOfDay())

    private fun normalize(value: String): String = value.replace(Regex("\\s+"), " ").trim()
}
