package kr.ac.ssu.ssutoday.api.config

import kr.ac.ssu.ssutoday.api.common.ApiResponseWriter
import kr.ac.ssu.ssutoday.core.status.SsuStatus
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
class SecurityConfig(
    private val authenticationFilter: AuthenticationFilter,
    private val clientKeyFilter: ClientKeyFilter,
    private val apiResponseWriter: ApiResponseWriter,
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain = http
        .httpBasic { it.disable() }
        .formLogin { it.disable() }
        .csrf { it.disable() }
        .cors { }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .authorizeHttpRequests {
            it.requestMatchers(
                "/student/login",
                "/device/checkVersion",
                "/sso/validateToken",
                "/error",
                "/actuator/health",
            ).permitAll()
                .anyRequest().authenticated()
        }
        .exceptionHandling {
            it.authenticationEntryPoint { _, response, _ ->
                writeFailure(response, SsuStatus.SSU4001)
            }
            it.accessDeniedHandler { _, response, _ ->
                writeFailure(response, SsuStatus.SSU4001)
            }
        }
        .addFilterBefore(authenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
        .addFilterBefore(clientKeyFilter, AuthenticationFilter::class.java)
        .build()

    private fun writeFailure(
        response: jakarta.servlet.http.HttpServletResponse,
        status: SsuStatus,
    ) = apiResponseWriter.write(response, status)
}
