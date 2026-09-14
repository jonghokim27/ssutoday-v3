package kr.ac.ssu.ssutoday.application.attest

import io.github.oshai.kotlinlogging.KotlinLogging
import kr.ac.ssu.ssutoday.application.attest.dto.RegisterAppAttestCommand
import kr.ac.ssu.ssutoday.application.attest.dto.RegisterAppAttestResult
import kr.ac.ssu.ssutoday.core.attestation.AttestationChallengeScope
import kr.ac.ssu.ssutoday.core.attestation.AttestationClientData
import kr.ac.ssu.ssutoday.core.attestation.AttestationPurpose
import kr.ac.ssu.ssutoday.core.attestation.AttestationVerdict
import kr.ac.ssu.ssutoday.core.exception.BusinessException
import kr.ac.ssu.ssutoday.core.port.AppAttestVerificationPort
import kr.ac.ssu.ssutoday.core.status.StatusCode
import kr.ac.ssu.ssutoday.domain.student.AttestChallengeService
import kr.ac.ssu.ssutoday.domain.student.DeviceAttestationService
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.util.Base64

@Service
class AppAttestRegistrationApplicationService(
    private val verifier: AppAttestVerificationPort,
    private val keys: DeviceAttestationService,
    private val challenges: AttestChallengeService,
    @Value("\${ssutoday.attestation.app-attest.production:true}") private val production: Boolean,
) {
    private val log = KotlinLogging.logger {}

    @Transactional
    fun register(command: RegisterAppAttestCommand): RegisterAppAttestResult {
        if (!AttestationClientData.isValidKeyId(command.keyId) ||
            !AttestationClientData.isValidChallenge(command.challenge) ||
            command.attestation.length !in 1..65536
        ) {
            reject(command.studentId, AttestationVerdict.INVALID_INPUT)
        }
        val bytes =
            try {
                Base64.getDecoder().decode(command.attestation)
            } catch (
                _: IllegalArgumentException,
            ) {
                reject(command.studentId, AttestationVerdict.INVALID_INPUT)
            }
        if (Base64.getEncoder().encodeToString(bytes) != command.attestation) reject(command.studentId, AttestationVerdict.INVALID_INPUT)
        val clientHash =
            AttestationClientData.hash(
                AttestationClientData.forAppAttestRegistration(command.studentId, command.challenge, command.keyId),
            )
        val fingerprint = AttestationClientData.hash(clientHash + bytes)
        val existing = keys.find(command.keyId)
        if (existing != null) {
            if (existing.studentId != command.studentId) reject(command.studentId, AttestationVerdict.KEY_OWNER_MISMATCH)
            if (existing.production != production) reject(command.studentId, AttestationVerdict.ENVIRONMENT_MISMATCH)
            // 등록 응답을 잃은 경우에만 같은 검증 완료 payload를 재전송할 수 있다. counter는 초기화하지 않는다.
            if (!MessageDigest.isEqual(existing.registrationHash, fingerprint)) reject(command.studentId, AttestationVerdict.INVALID_INPUT)
            return RegisterAppAttestResult(command.keyId)
        }
        val verified = verifier.verifyRegistration(command.attestation, command.keyId, clientHash)
        if (verified.verdict != AttestationVerdict.VERIFIED) reject(command.studentId, verified.verdict)
        challenges.consume(command.challenge, AttestationChallengeScope(command.studentId, AttestationPurpose.APP_ATTEST_REGISTER))
        try {
            keys.register(command.studentId, command.keyId, requireNotNull(verified.publicKey), production, fingerprint)
        } catch (_: DataIntegrityViolationException) {
            reject(command.studentId, AttestationVerdict.KEY_OWNER_MISMATCH)
        }
        log.info { "App Attest registration: studentId=${command.studentId} verdict=VERIFIED production=$production" }
        return RegisterAppAttestResult(command.keyId)
    }

    private fun reject(
        studentId: Int,
        verdict: AttestationVerdict,
    ): Nothing {
        log.info { "App Attest registration: studentId=$studentId verdict=$verdict production=$production" }
        throw BusinessException(StatusCode.SSU4206)
    }
}
