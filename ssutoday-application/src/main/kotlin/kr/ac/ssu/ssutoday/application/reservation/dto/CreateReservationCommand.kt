package kr.ac.ssu.ssutoday.application.reservation.dto

import java.time.LocalDate

data class CreateReservationCommand(
    val turnstileToken: String,
    val studentId: Int,
    val major: String,
    val admin: Boolean,
    val roomNo: String,
    val date: LocalDate,
    val startBlock: Int,
    val endBlock: Int,
)
