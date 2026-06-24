package kr.ac.ssu.ssutoday.api.config

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kr.ac.ssu.ssutoday.application.student.StudentApplicationService
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class AuthenticationFilter(
    private val studentApplicationService: StudentApplicationService,
    private val tokenCookieWriter: TokenCookieWriter,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val access = request.readCookie(TokenCookieWriter.ACCESS_TOKEN_COOKIE)
        val refresh = request.readCookie(TokenCookieWriter.REFRESH_TOKEN_COOKIE)
        if (!access.isNullOrBlank() && !refresh.isNullOrBlank()) {
            runCatching { studentApplicationService.validate(access, refresh) }
                .onSuccess {
                    val renewedAccess = it.accessToken
                    val renewedRefresh = it.refreshToken
                    if (renewedAccess != null && renewedRefresh != null) {
                        tokenCookieWriter.writeAuthCookies(response, renewedAccess, renewedRefresh)
                    }
                    SecurityContextHolder.getContext().authentication =
                        UsernamePasswordAuthenticationToken(it.student, null, emptyList())
                }.onFailure {
                    log.debug(it) { "Authentication token validation failed" }
                }
        }
        chain.doFilter(request, response)
    }

    private companion object {
        val log = KotlinLogging.logger {}
    }
}
