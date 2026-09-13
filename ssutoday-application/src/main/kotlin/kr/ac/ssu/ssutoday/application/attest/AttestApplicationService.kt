package kr.ac.ssu.ssutoday.application.attest

import kr.ac.ssu.ssutoday.application.attest.dto.AttestChallengeResult
import kr.ac.ssu.ssutoday.application.attest.dto.CreateAttestChallengeCommand
import kr.ac.ssu.ssutoday.core.attestation.AttestationChallengeScope
import kr.ac.ssu.ssutoday.core.attestation.AttestationPurpose
import kr.ac.ssu.ssutoday.core.exception.InvalidInputException
import kr.ac.ssu.ssutoday.domain.reservation.ReservationService
import kr.ac.ssu.ssutoday.domain.student.AttestChallengeService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AttestApplicationService(
    private val challengeService: AttestChallengeService,
    private val reservationService: ReservationService,
) {
    @Transactional
    fun createChallenge(command: CreateAttestChallengeCommand): AttestChallengeResult {
        when (command.purpose) {
            AttestationPurpose.VERIFY_PHOTO_UPLOAD -> {
                val reservationId = command.reservationId
                if (reservationId == null || reservationId <= 0) {
                    throw InvalidInputException("An upload challenge requires a positive reservationId")
                }
                reservationService.getForPhotoUpload(command.studentId, reservationId)
            }
            AttestationPurpose.APP_ATTEST_REGISTER, AttestationPurpose.RESERVATION_CREATE -> {
                if (command.reservationId != null) {
                    throw InvalidInputException("This challenge purpose must not contain reservationId")
                }
            }
        }
        val scope = AttestationChallengeScope(command.studentId, command.purpose, command.reservationId)
        val challenge = challengeService.create(scope)
        return AttestChallengeResult(
            challenge.challenge,
            challenge.expiresInSeconds,
            command.studentId,
            command.purpose,
            command.reservationId,
        )
    }
}
