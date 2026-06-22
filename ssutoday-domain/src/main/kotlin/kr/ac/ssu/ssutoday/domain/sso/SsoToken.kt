package kr.ac.ssu.ssutoday.domain.sso

import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash

@RedisHash(value = "ssoToken", timeToLive = 60)
data class SsoToken(
    @Id val token: String,
    val clientId: String,
    val studentId: Int,
    val name: String,
    val major: String,
)
