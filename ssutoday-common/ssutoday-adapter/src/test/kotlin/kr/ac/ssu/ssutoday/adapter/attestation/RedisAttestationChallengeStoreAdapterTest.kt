package kr.ac.ssu.ssutoday.adapter.attestation

import kr.ac.ssu.ssutoday.core.attestation.AttestationChallengeScope
import kr.ac.ssu.ssutoday.core.attestation.AttestationPurpose
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Testcontainers(disabledWithoutDocker = true)
class RedisAttestationChallengeStoreAdapterTest {
    private val scope = AttestationChallengeScope(20260000, AttestationPurpose.VERIFY_PHOTO_UPLOAD, 42)
    private val challenge = UUID.randomUUID().toString()
    private lateinit var connectionFactory: LettuceConnectionFactory
    private lateinit var redis: StringRedisTemplate
    private lateinit var adapter: RedisAttestationChallengeStoreAdapter

    @BeforeEach
    fun setUp() {
        connectionFactory = LettuceConnectionFactory(container.host, container.getMappedPort(6379))
        connectionFactory.afterPropertiesSet()
        redis = StringRedisTemplate(connectionFactory)
        adapter = RedisAttestationChallengeStoreAdapter(redis)
    }

    @AfterEach
    fun tearDown() {
        connectionFactory.destroy()
    }

    @Test
    fun `Redis에 60초 TTL로 저장하고 기존 키를 덮어쓰지 않는다`() {
        assertTrue(adapter.create(challenge, scope, Duration.ofSeconds(60)))
        assertTrue(redis.getExpire("attestChallenge:v1:$challenge") in 1L..60L)

        assertFalse(adapter.create(challenge, scope.copy(studentId = 20260001), Duration.ofSeconds(120)))
        assertTrue(redis.getExpire("attestChallenge:v1:$challenge") in 1L..60L)
        assertTrue(adapter.consume(challenge, scope))
        assertFalse(adapter.consume(challenge, scope))
    }

    @Test
    fun `사용자 예약 용도가 다르면 소모하지 않는다`() {
        adapter.create(challenge, scope, Duration.ofSeconds(60))

        assertFalse(adapter.consume(challenge, scope.copy(studentId = 20260001)))
        assertFalse(adapter.consume(challenge, scope.copy(reservationId = 43)))
        assertFalse(adapter.consume(challenge, AttestationChallengeScope(20260000, AttestationPurpose.APP_ATTEST_REGISTER)))
        assertTrue(adapter.consume(challenge, scope))
    }

    @Test
    fun `만료한 challenge는 Redis에서 소멸하고 소모할 수 없다`() {
        adapter.create(challenge, scope, Duration.ofMillis(100))
        Thread.sleep(200)

        assertFalse(redis.hasKey("attestChallenge:v1:$challenge"))
        assertFalse(adapter.consume(challenge, scope))
    }

    @Test
    fun `동시에 소모해도 단 한 요청만 성공한다`() {
        adapter.create(challenge, scope, Duration.ofSeconds(60))
        val ready = CountDownLatch(16)
        val start = CountDownLatch(1)

        Executors.newFixedThreadPool(16).use { executor ->
            val results =
                List(16) {
                    executor.submit<Boolean> {
                        ready.countDown()
                        check(start.await(5, TimeUnit.SECONDS))
                        adapter.consume(challenge, scope)
                    }
                }
            try {
                assertTrue(ready.await(5, TimeUnit.SECONDS))
            } finally {
                start.countDown()
            }
            assertEquals(1, results.count { it.get(5, TimeUnit.SECONDS) })
        }
    }

    private companion object {
        @Container
        @JvmStatic
        val container = GenericContainer(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379)
    }
}
