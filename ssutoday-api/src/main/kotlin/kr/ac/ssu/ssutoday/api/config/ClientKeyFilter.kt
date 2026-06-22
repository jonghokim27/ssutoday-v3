package kr.ac.ssu.ssutoday.api.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kr.ac.ssu.ssutoday.api.common.ApiResponseWriter
import kr.ac.ssu.ssutoday.core.status.SsuStatus
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class ClientKeyFilter(
    @Value("\${spring.client.key}") private val clientKey: String,
    private val apiResponseWriter: ApiResponseWriter,
) : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        if (request.requestURI == "/sso/validateToken" || request.getHeader("Client-Key") == clientKey) {
            chain.doFilter(request, response)
            return
        }
        apiResponseWriter.write(response, SsuStatus.SSU4003)
    }
}
