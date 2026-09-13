package kr.ac.ssu.ssutoday.application.reservation.dto

import kr.ac.ssu.ssutoday.application.attest.dto.AttestationEvidence
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
    val attestation: AttestationEvidence = AttestationEvidence(),
)
