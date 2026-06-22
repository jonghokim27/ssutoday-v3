package kr.ac.ssu.ssutoday.application.reservation.dto

import java.sql.Date

data class CreateReservationCommand(
    val recaptchaToken: String,
    val studentId: Int,
    val major: String,
    val admin: Boolean,
    val roomNo: String,
    val date: Date,
    val startBlock: Int,
    val endBlock: Int,
)
