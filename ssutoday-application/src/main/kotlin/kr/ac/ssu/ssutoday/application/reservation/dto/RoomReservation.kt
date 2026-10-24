package kr.ac.ssu.ssutoday.application.reservation.dto

data class RoomReservation(
    val idx: Long?,
    val studentInfo: StudentInfo,
    val startBlock: Int,
    val endBlock: Int,
    val isMine: Boolean,
    val verifyPhotoUrl: String?,
    /** 페널티 대상 학과 학생의 예약인지. 관리자에게만 내려주고 그 외에는 null이다. */
    val penaltyTarget: Boolean? = null,
) {
    data class StudentInfo(
        val studentId: String,
        val name: String,
        val major: String,
    )
}
