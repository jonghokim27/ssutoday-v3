package kr.ac.ssu.ssutoday.domain.student

import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash

@RedisHash(value = "refreshToken", timeToLive = 3600 * 24 * 120L)
data class RefreshToken(
    @Id val refreshToken: String,
    val accessToken: String,
    val studentId: Int,
    val name: String,
    val major: String,
)
