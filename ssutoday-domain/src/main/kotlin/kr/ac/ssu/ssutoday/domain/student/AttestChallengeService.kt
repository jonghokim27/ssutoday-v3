package kr.ac.ssu.ssutoday.domain.student

import kr.ac.ssu.ssutoday.core.attestation.AttestationChallengeScope
import kr.ac.ssu.ssutoday.core.attestation.AttestationClientData
import kr.ac.ssu.ssutoday.core.exception.BusinessException
import kr.ac.ssu.ssutoday.core.port.AttestationChallengeStorePort
import kr.ac.ssu.ssutoday.core.status.StatusCode
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64

@Service
class AttestChallengeService(
    private val challengeStore: AttestationChallengeStorePort,
) {
    private val random = SecureRandom()

    fun create(scope: AttestationChallengeScope): AttestChallengeView {
        repeat(3) {
            val bytes = ByteArray(32).also(random::nextBytes)
            val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            if (challengeStore.create(challenge, scope, Duration.ofSeconds(TTL_SECONDS))) {
                return AttestChallengeView(challenge, TTL_SECONDS)
            }
        }
        throw IllegalStateException("Failed to create a unique attestation challenge")
    }

    /** 증명 검증 성공 후, 업로드/키 등록의 상태 변경 전에 호출한다. */
    fun consume(
        challenge: String,
        scope: AttestationChallengeScope,
    ) {
        if (!AttestationClientData.isValidChallenge(challenge) || !challengeStore.consume(challenge, scope)) {
            throw BusinessException(StatusCode.SSU4206)
        }
    }

    private companion object {
        const val TTL_SECONDS = 60L
    }
}
