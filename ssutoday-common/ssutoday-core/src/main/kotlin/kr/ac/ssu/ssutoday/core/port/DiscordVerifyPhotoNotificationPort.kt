package kr.ac.ssu.ssutoday.core.port

import java.time.LocalDate

interface DiscordVerifyPhotoNotificationPort {
    fun send(
        reservationId: Long,
        adminToken: String,
        studentId: Int,
        studentName: String,
        roomNo: String,
        roomName: String,
        date: LocalDate,
        startBlock: Int,
        endBlock: Int,
        photoUrl: String,
    )
}
