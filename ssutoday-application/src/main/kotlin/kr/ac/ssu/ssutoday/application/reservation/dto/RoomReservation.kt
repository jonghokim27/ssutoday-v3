package kr.ac.ssu.ssutoday.application.reservation.dto

data class RoomReservation(
    val idx: Long?,
    val studentInfo: StudentInfo,
    val startBlock: Int,
    val endBlock: Int,
    val isMine: Boolean,
) {
    data class StudentInfo(
        val studentId: String,
        val name: String,
        val major: String
    )
}
