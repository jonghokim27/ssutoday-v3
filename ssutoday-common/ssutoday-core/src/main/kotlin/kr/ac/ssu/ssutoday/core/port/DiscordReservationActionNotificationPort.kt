package kr.ac.ssu.ssutoday.core.port

interface DiscordReservationActionNotificationPort {
    fun send(
        content: String,
        reservationId: Long,
        studentInfo: String,
        roomName: String,
        reservationDateTime: String,
        actionFieldName: String,
        actionFieldValue: String,
        photoUrl: String? = null,
    )
}
