package kr.ac.ssu.ssutoday.core.port

import java.time.LocalDate

interface DiscordVerifyPhotoNotificationPort {
    fun send(
        content: String,
        reservationId: Long,
        adminToken: String,
        studentInfo: String,
        roomName: String,
        reservationDateTime: String,
        photoUrl: String,
    )
}
