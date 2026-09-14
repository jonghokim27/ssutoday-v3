package kr.ac.ssu.ssutoday.domain.student

import kr.ac.ssu.ssutoday.core.attestation.AttestationChallengeScope
import kr.ac.ssu.ssutoday.core.attestation.AttestationClientData
import kr.ac.ssu.ssutoday.core.attestation.AttestationPurpose
import kr.ac.ssu.ssutoday.core.exception.BusinessException
import kr.ac.ssu.ssutoday.core.port.AttestationChallengeStorePort
import kr.ac.ssu.ssutoday.core.status.StatusCode
import java.time.Duration
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AttestChallengeServiceTest {
    private val scope = AttestationChallengeScope(20260000, AttestationPurpose.VERIFY_PHOTO_UPLOAD, 42)
    private val store = MemoryStore()
    private val service = AttestChallengeService(store)

    @Test
    fun `호출마다 32바이트 난수를 생성하고 60초 TTL로 저장한다`() {
        val challenges = List(100) { service.create(scope) }

        assertEquals(100, challenges.map { it.challenge }.toSet().size)
        assertTrue(challenges.all { it.expiresInSeconds == 60L && AttestationClientData.isValidChallenge(it.challenge) })
        assertTrue(challenges.all { Base64.getUrlDecoder().decode(it.challenge).size == 32 })
        assertEquals(Duration.ofSeconds(60), store.lastTtl)
    }

    @Test
    fun `재사용과 소유자가 다른 요청은 거부하고 정상 소유자의 사용은 유지한다`() {
        val challenge = service.create(scope).challenge
        val wrongOwner = assertFailsWith<BusinessException> { service.consume(challenge, scope.copy(studentId = 20260001)) }
        assertEquals(StatusCode.SSU4206, wrongOwner.status)

        service.consume(challenge, scope)

        val replay = assertFailsWith<BusinessException> { service.consume(challenge, scope) }
        assertEquals(StatusCode.SSU4206, replay.status)
    }

    @Test
    fun `저장 충돌은 재발급하고 반복 충돌은 내부 오류로 처리한다`() {
        store.rejectCreations = 1
        service.create(scope)
        assertEquals(2, store.createCalls)

        store.rejectCreations = 3
        assertFailsWith<IllegalStateException> { service.create(scope) }
    }

    @Test
    fun `형식이 잘못된 challenge는 저장소에 전달하지 않는다`() {
        assertFailsWith<BusinessException> { service.consume("invalid", scope) }
        assertEquals(0, store.consumeCalls)
    }

    private class MemoryStore : AttestationChallengeStorePort {
        private val entries = mutableMapOf<String, AttestationChallengeScope>()
        var lastTtl: Duration? = null
        var rejectCreations = 0
        var createCalls = 0
        var consumeCalls = 0

        override fun create(
            challenge: String,
            scope: AttestationChallengeScope,
            ttl: Duration,
        ): Boolean {
            createCalls++
            lastTtl = ttl
            if (rejectCreations-- > 0) return false
            return entries.putIfAbsent(challenge, scope) == null
        }

        override fun consume(
            challenge: String,
            scope: AttestationChallengeScope,
        ): Boolean {
            consumeCalls++
            return entries.remove(challenge, scope)
        }
    }
}
