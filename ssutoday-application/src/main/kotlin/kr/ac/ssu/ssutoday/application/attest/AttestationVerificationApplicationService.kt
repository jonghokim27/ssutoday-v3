package kr.ac.ssu.ssutoday.application.attest

import io.github.oshai.kotlinlogging.KotlinLogging
import kr.ac.ssu.ssutoday.application.attest.dto.AttestationEvidence
import kr.ac.ssu.ssutoday.application.attest.dto.AttestationResult
import kr.ac.ssu.ssutoday.application.attest.dto.VerifyPhotoAttestationCommand
import kr.ac.ssu.ssutoday.application.reservation.dto.CreateReservationCommand
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
class AttestationVerificationApplicationService(
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
    fun verify(command: VerifyPhotoAttestationCommand): AttestationResult =
        verify(
            AttestationChallengeScope(command.studentId, AttestationPurpose.VERIFY_PHOTO_UPLOAD, command.reservationId),
            command.evidence,
        ) { challenge ->
            val photoHash = HexFormat.of().formatHex(AttestationClientData.hash(command.photo))
            AttestationClientData.forPhotoUpload(command.studentId, command.reservationId, challenge, photoHash)
        }

    @Transactional
    fun verifyReservation(command: CreateReservationCommand): AttestationResult =
        verify(AttestationChallengeScope(command.studentId, AttestationPurpose.RESERVATION_CREATE), command.attestation) { challenge ->
            AttestationClientData.forReservation(
                command.studentId,
                command.roomNo,
                command.date,
                command.startBlock,
                command.endBlock,
                challenge,
            )
        }

    private fun verify(
        scope: AttestationChallengeScope,
        evidence: AttestationEvidence,
        clientData: (String) -> ByteArray,
    ): AttestationResult {
        val verdict = evaluate(scope, evidence, clientData)
        val platform =
            when (evidence.platform) {
                "android" -> "android"
                "ios" -> "ios"
                null -> "missing"
                else -> "unknown"
            }
        log.info {
            "Attestation: purpose=${scope.purpose} studentId=${scope.studentId} reservationId=${scope.reservationId} " +
                "platform=$platform verdict=$verdict enforce=$enforce " +
                // 진단용. 증명 원문은 남기지 않고 도달 여부와 길이만 본다. keyId는 DB에 평문으로 있는 공개 식별자다.
                "challengeLen=${evidence.challenge?.length} attestationLen=${evidence.attestation?.length} keyId=${evidence.keyId}"
        }
        if (enforce && verdict != AttestationVerdict.VERIFIED) throw BusinessException(StatusCode.SSU4206)
        return AttestationResult(verdict, platform, enforce)
    }

    private fun evaluate(
        scope: AttestationChallengeScope,
        evidence: AttestationEvidence,
        clientData: (String) -> ByteArray,
    ): AttestationVerdict {
        if (evidence.platform == null &&
            evidence.challenge == null &&
            evidence.attestation == null &&
            evidence.keyId == null
        ) {
            return AttestationVerdict.MISSING
        }
        if (evidence.platform.isNullOrBlank() ||
            evidence.challenge.isNullOrBlank() ||
            evidence.attestation.isNullOrBlank()
        ) {
            return AttestationVerdict.INVALID_INPUT
        }
        if (evidence.platform != "android" && evidence.platform != "ios") return AttestationVerdict.UNSUPPORTED_PLATFORM
        if (!AttestationClientData.isValidChallenge(evidence.challenge)) return AttestationVerdict.INVALID_INPUT
        if (evidence.attestation.length > MAX_TOKEN_LENGTH ||
            evidence.attestation.any(Char::isWhitespace)
        ) {
            return AttestationVerdict.INVALID_INPUT
        }
        val data =
            try {
                clientData(evidence.challenge)
            } catch (_: IllegalArgumentException) {
                return AttestationVerdict.INVALID_INPUT
            }
        // 진단용. 서명 대상 바이트를 앱이 만든 것과 대조한다. 비밀값은 없다. 원인 확인 후 제거한다.
        log.warn { "clientData=" + data.decodeToString().replace('\n', '|') }
        if (evidence.platform == "ios") return verifyIos(scope, evidence, AttestationClientData.hash(data))
        if (evidence.keyId != null) return AttestationVerdict.INVALID_INPUT
        val verdict = playIntegrityVerificationPort.verify(evidence.attestation, AttestationClientData.requestHash(data))
        if (verdict != AttestationVerdict.VERIFIED) return verdict
        return consumeChallenge(scope, evidence.challenge)
    }

    private fun verifyIos(
        scope: AttestationChallengeScope,
        evidence: AttestationEvidence,
        clientDataHash: ByteArray,
    ): AttestationVerdict {
        val keyId = evidence.keyId
        if (keyId == null || !AttestationClientData.isValidKeyId(keyId)) return AttestationVerdict.INVALID_INPUT
        return try {
            val key = deviceAttestationService.find(keyId) ?: return AttestationVerdict.KEY_NOT_REGISTERED
            if (key.studentId != scope.studentId) return AttestationVerdict.KEY_OWNER_MISMATCH
            if (key.production != appAttestProduction) return AttestationVerdict.ENVIRONMENT_MISMATCH
            val verified = appAttestVerificationPort.verifyAssertion(requireNotNull(evidence.attestation), key.publicKey, clientDataHash)
            if (verified.verdict != AttestationVerdict.VERIFIED) return verified.verdict
            val counter = requireNotNull(verified.counter)
            if (counter <= key.counter) return AttestationVerdict.COUNTER_REJECTED
            val challengeVerdict = consumeChallenge(scope, requireNotNull(evidence.challenge))
            if (challengeVerdict != AttestationVerdict.VERIFIED) return challengeVerdict
            if (deviceAttestationService.advanceCounter(
                    keyId,
                    scope.studentId,
                    appAttestProduction,
                    counter,
                )
            ) {
                AttestationVerdict.VERIFIED
            } else {
                AttestationVerdict.COUNTER_REJECTED
            }
        } catch (_: DataAccessException) {
            AttestationVerdict.KEY_STORE_UNAVAILABLE
        }
    }

    private fun consumeChallenge(
        scope: AttestationChallengeScope,
        challenge: String,
    ): AttestationVerdict =
        try {
            challengeService.consume(challenge, scope)
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
