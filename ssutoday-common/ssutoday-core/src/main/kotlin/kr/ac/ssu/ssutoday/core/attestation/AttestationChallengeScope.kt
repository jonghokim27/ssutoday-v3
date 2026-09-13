package kr.ac.ssu.ssutoday.core.attestation

data class AttestationChallengeScope(
    val studentId: Int,
    val purpose: AttestationPurpose,
    val reservationId: Long? = null,
) {
    init {
        require(studentId > 0)
        when (purpose) {
            AttestationPurpose.VERIFY_PHOTO_UPLOAD -> require(reservationId != null && reservationId > 0)
            AttestationPurpose.APP_ATTEST_REGISTER, AttestationPurpose.RESERVATION_CREATE -> require(reservationId == null)
        }
    }
}
