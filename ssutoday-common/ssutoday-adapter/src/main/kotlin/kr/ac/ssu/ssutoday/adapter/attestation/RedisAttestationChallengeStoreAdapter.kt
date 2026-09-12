package kr.ac.ssu.ssutoday.adapter.attestation

import kr.ac.ssu.ssutoday.core.attestation.AttestationChallengeScope
import kr.ac.ssu.ssutoday.core.port.AttestationChallengeStorePort
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class RedisAttestationChallengeStoreAdapter(
    private val redis: StringRedisTemplate,
) : AttestationChallengeStorePort {
    override fun create(
        challenge: String,
        scope: AttestationChallengeScope,
        ttl: Duration,
    ): Boolean = redis.opsForValue().setIfAbsent(key(challenge), scopeValue(scope), ttl) == true

    override fun consume(
        challenge: String,
        scope: AttestationChallengeScope,
    ): Boolean = redis.execute(consumeScript, listOf(key(challenge)), scopeValue(scope)) == 1L

    private fun key(challenge: String): String = "attestChallenge:v1:$challenge"

    private fun scopeValue(scope: AttestationChallengeScope): String =
        "${scope.studentId}:${scope.purpose.name}:${scope.reservationId ?: ""}"

    private companion object {
        val consumeScript =
            DefaultRedisScript(
                """
                if redis.call('GET', KEYS[1]) ~= ARGV[1] then
                    return 0
                end
                return redis.call('DEL', KEYS[1])
                """.trimIndent(),
                Long::class.javaObjectType,
            )
    }
}
