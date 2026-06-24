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
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val access =
            request
                .getHeader("Authorization")
                ?.takeIf { it.startsWith(BEARER_PREFIX) }
                ?.removePrefix(BEARER_PREFIX)
        val refresh = request.getHeader("Refresh-Token")
        if (!access.isNullOrBlank() && !refresh.isNullOrBlank()) {
            runCatching { studentApplicationService.validate(access, refresh) }
                .onSuccess {
                    it.accessToken?.let { token -> response.setHeader("Access-Token", token) }
                    it.refreshToken?.let { token -> response.setHeader("Refresh-Token", token) }
                    SecurityContextHolder.getContext().authentication =
                        UsernamePasswordAuthenticationToken(it.student, null, emptyList())
                }.onFailure {
                    log.debug(it) { "Authentication token validation failed" }
                }
        }
        chain.doFilter(request, response)
    }

    private companion object {
        const val BEARER_PREFIX = "Bearer "
        val log = KotlinLogging.logger {}
    }
}
