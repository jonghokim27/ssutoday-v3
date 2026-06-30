package kr.ac.ssu.ssutoday.migration.job

import java.sql.Date
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime

internal fun Map<String, Any?>.timestamp(column: String): Timestamp =
    nullableTimestamp(column) ?: error("Required timestamp column is null: $column")

internal fun Map<String, Any?>.nullableTimestamp(column: String): Timestamp? =
    when (val value = this[column]) {
        null -> null
        is Timestamp -> value
        is LocalDateTime -> Timestamp.valueOf(value)
        is java.util.Date -> Timestamp(value.time)
        is String -> Timestamp.valueOf(value)
        else -> error("Unsupported timestamp column type: $column=${value::class.qualifiedName}")
    }

internal fun Map<String, Any?>.date(column: String): Date =
    when (val value = this[column]) {
        is Date -> value
        is LocalDate -> Date.valueOf(value)
        is LocalDateTime -> Date.valueOf(value.toLocalDate())
        is Timestamp -> Date(value.time)
        is java.util.Date -> Date(value.time)
        is String -> Date.valueOf(value)
        null -> error("Required date column is null: $column")
        else -> error("Unsupported date column type: $column=${value::class.qualifiedName}")
    }
