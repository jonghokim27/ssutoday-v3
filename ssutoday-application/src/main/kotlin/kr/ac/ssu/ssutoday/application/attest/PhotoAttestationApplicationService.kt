package kr.ac.ssu.ssutoday.application.attest

import io.github.oshai.kotlinlogging.KotlinLogging
import kr.ac.ssu.ssutoday.application.attest.dto.PhotoAttestationResult
import kr.ac.ssu.ssutoday.application.attest.dto.VerifyPhotoAttestationCommand
import kr.ac.ssu.ssutoday.core.attestation.AttestationChallengeScope
import kr.ac.ssu.ssutoday.core.attestation.AttestationClientData
import kr.ac.ssu.ssutoday.core.attestation.AttestationPurpose
import kr.ac.ssu.ssutoday.core.attestation.AttestationVerdict
import kr.ac.ssu.ssutoday.core.exception.BusinessException
import kr.ac.ssu.ssutoday.core.port.AppAttestVerificationPort
import kr.ac.ssu.ssutoday.core.port.PlayIntegrityVerificationPort
import kr.ac.ssu.ssutoday.core.status.StatusCode
import kr.ac.ssu.ssutoday.domain.student.AttestChallengeService
import kr.ac.ssu.ssutoday.domain.student.DeviceAttestationService
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.HexFormat

@Service
class PhotoAttestationApplicationService(
    private val playIntegrityVerificationPort: PlayIntegrityVerificationPort,
    private val challengeService: AttestChallengeService,
    @Value("\${ssutoday.attestation.enforce:false}")
    private val enforce: Boolean,
    private val appAttestVerificationPort: AppAttestVerificationPort,
    private val deviceAttestationService: DeviceAttestationService,
    @Value("\${ssutoday.attestation.app-attest.production:true}")
    private val appAttestProduction: Boolean,
) {
    private val log = KotlinLogging.logger {}

    @Transactional
    fun verify(command: VerifyPhotoAttestationCommand): PhotoAttestationResult {
        val verdict = evaluate(command)
        val platform =
            when (command.evidence.platform) {
                "android" -> "android"
                "ios" -> "ios"
                null -> "missing"
                else -> "unknown"
            }
        log.info {
            "Photo attestation: studentId=${command.studentId} reservationId=${command.reservationId} " +
                "platform=$platform verdict=$verdict enforce=$enforce"
        }
        if (enforce && verdict != AttestationVerdict.VERIFIED) throw BusinessException(StatusCode.SSU4206)
        return PhotoAttestationResult(verdict, platform, enforce)
    }

    private fun evaluate(command: VerifyPhotoAttestationCommand): AttestationVerdict {
        val evidence = command.evidence
        if (evidence.platform == null && evidence.challenge == null && evidence.attestation == null && evidence.keyId == null) {
            return AttestationVerdict.MISSING
        }
        if (evidence.platform.isNullOrBlank() || evidence.challenge.isNullOrBlank() || evidence.attestation.isNullOrBlank()) {
            return AttestationVerdict.INVALID_INPUT
        }
        if (evidence.platform != "android" && evidence.platform != "ios") return AttestationVerdict.UNSUPPORTED_PLATFORM
        if (!AttestationClientData.isValidChallenge(evidence.challenge)) {
            return AttestationVerdict.INVALID_INPUT
        }
        if (evidence.attestation.length > MAX_TOKEN_LENGTH || evidence.attestation.any(Char::isWhitespace)) {
            return AttestationVerdict.INVALID_INPUT
        }

        val photoHash = HexFormat.of().formatHex(AttestationClientData.hash(command.photo))
        val clientData =
            AttestationClientData.forPhotoUpload(command.studentId, command.reservationId, evidence.challenge, photoHash)
        if (evidence.platform == "ios") return verifyIos(command, AttestationClientData.hash(clientData))
        if (evidence.keyId != null) return AttestationVerdict.INVALID_INPUT
        val verdict = playIntegrityVerificationPort.verify(evidence.attestation, AttestationClientData.requestHash(clientData))
        if (verdict != AttestationVerdict.VERIFIED) return verdict

        return consumeChallenge(command)
    }

    private fun verifyIos(
        command: VerifyPhotoAttestationCommand,
        clientDataHash: ByteArray,
    ): AttestationVerdict {
        val keyId = command.evidence.keyId
        if (keyId == null || !AttestationClientData.isValidKeyId(keyId)) return AttestationVerdict.INVALID_INPUT
        return try {
            val key = deviceAttestationService.find(keyId) ?: return AttestationVerdict.KEY_NOT_REGISTERED
            if (key.studentId != command.studentId) return AttestationVerdict.KEY_OWNER_MISMATCH
            if (key.production != appAttestProduction) return AttestationVerdict.ENVIRONMENT_MISMATCH
            val verified =
                appAttestVerificationPort.verifyAssertion(
                    requireNotNull(command.evidence.attestation),
                    key.publicKey,
                    clientDataHash,
                )
            if (verified.verdict != AttestationVerdict.VERIFIED) return verified.verdict
            val counter = requireNotNull(verified.counter)
            if (counter <= key.counter) return AttestationVerdict.COUNTER_REJECTED
            val challengeVerdict = consumeChallenge(command)
            if (challengeVerdict != AttestationVerdict.VERIFIED) return challengeVerdict
            if (deviceAttestationService.advanceCounter(keyId, command.studentId, appAttestProduction, counter)) {
                AttestationVerdict.VERIFIED
            } else {
                AttestationVerdict.COUNTER_REJECTED
            }
        } catch (_: DataAccessException) {
            AttestationVerdict.KEY_STORE_UNAVAILABLE
        }
    }

    private fun consumeChallenge(command: VerifyPhotoAttestationCommand): AttestationVerdict =
        try {
            challengeService.consume(
                requireNotNull(command.evidence.challenge),
                AttestationChallengeScope(command.studentId, AttestationPurpose.VERIFY_PHOTO_UPLOAD, command.reservationId),
            )
            AttestationVerdict.VERIFIED
        } catch (exception: BusinessException) {
            if (exception.status != StatusCode.SSU4206) throw exception
            AttestationVerdict.CHALLENGE_REJECTED
        } catch (_: DataAccessException) {
            AttestationVerdict.CHALLENGE_STORE_UNAVAILABLE
        }

    private companion object {
        const val MAX_TOKEN_LENGTH = 32 * 1024
    }
}
