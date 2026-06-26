package kr.ac.ssu.ssutoday.api.config

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.time.Duration

fun HttpServletRequest.readCookie(name: String): String? = cookies?.firstOrNull { it.name == name }?.value

@Component
class TokenCookieWriter {
    fun writeAuthCookies(
        response: HttpServletResponse,
        accessToken: String,
        refreshToken: String,
        persist: Boolean = false,
    ) {
        val maxAge = if (persist) Duration.ofDays(365) else REFRESH_TOKEN_MAX_AGE
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(ACCESS_TOKEN_COOKIE, accessToken, maxAge).toString())
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(REFRESH_TOKEN_COOKIE, refreshToken, maxAge).toString())
    }

    fun clearAuthCookies(response: HttpServletResponse) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(ACCESS_TOKEN_COOKIE, "", Duration.ZERO).toString())
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(REFRESH_TOKEN_COOKIE, "", Duration.ZERO).toString())
    }

    private fun cookie(
        name: String,
        value: String,
        maxAge: Duration,
    ): ResponseCookie =
        ResponseCookie
            .from(name, value)
            .httpOnly(true)
            .secure(true)
            .sameSite("Lax")
            .domain("api.ssu.today")
            .path("/")
            .maxAge(maxAge)
            .build()

    companion object {
        const val ACCESS_TOKEN_COOKIE = "accessToken"
        const val REFRESH_TOKEN_COOKIE = "refreshToken"
        private val ACCESS_TOKEN_MAX_AGE: Duration = Duration.ofHours(2)
        private val REFRESH_TOKEN_MAX_AGE: Duration = Duration.ofDays(120)
    }
}
