package kr.ac.ssu.ssutoday.core.port

import java.time.LocalDate

interface DiscordReservationActionNotificationPort {
    fun send(
        title: String,
        reservationId: Long,
        studentId: Int,
        roomNo: String,
        date: LocalDate,
        startBlock: Int,
        endBlock: Int,
        reason: String,
        photoUrl: String? = null,
    )
}
