package kr.ac.ssu.ssutoday.adapter.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JwtTokenAdapterTest {
    private val adapter = JwtTokenAdapter("test-secret")

    @Test
    fun `랜덤 토큰은 기존 서버와 동일한 소문자 16진수 형식이다`() {
        val token = adapter.randomToken(50)

        assertEquals(50, token.length)
        assertTrue(token.matches(Regex("[0-9a-f]{50}")))
    }
}
