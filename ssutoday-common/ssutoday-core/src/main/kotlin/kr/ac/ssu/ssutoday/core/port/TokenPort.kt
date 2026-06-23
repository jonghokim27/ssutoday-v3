package kr.ac.ssu.ssutoday.core.port

import kr.ac.ssu.ssutoday.core.dto.TokenPayload

interface TokenPort {
    fun createAccessToken(payload: TokenPayload): String

    fun validateAccessToken(token: String): TokenPayload

    fun randomToken(length: Int = 50): String
}
