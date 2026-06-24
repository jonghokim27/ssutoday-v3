package kr.ac.ssu.ssutoday.api.config

import kr.ac.ssu.ssutoday.api.common.ApiResponseWriter
import kr.ac.ssu.ssutoday.core.status.StatusCode
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
class SecurityConfig(
    private val authenticationFilter: AuthenticationFilter,
    private val apiResponseWriter: ApiResponseWriter,
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .csrf { it.disable() }
            .cors { }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it
                    .requestMatchers(
                        "/student/login",
                        "/device/checkVersion",
                        "/sso/validateToken",
                        "/error",
                        "/actuator/health",
                    ).permitAll()
                    .anyRequest()
                    .authenticated()
            }.exceptionHandling {
                it.authenticationEntryPoint { _, response, _ ->
                    writeFailure(response, StatusCode.SSU4001)
                }
                it.accessDeniedHandler { _, response, _ ->
                    writeFailure(response, StatusCode.SSU4003)
                }
            }.addFilterBefore(authenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration =
            CorsConfiguration().apply {
                allowedOrigins = listOf("http://localhost:5173")
                allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                allowedHeaders = listOf("*")
                allowCredentials = true
            }

        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", configuration)
        }
    }

    private fun writeFailure(
        response: jakarta.servlet.http.HttpServletResponse,
        status: StatusCode,
    ) = apiResponseWriter.write(response, status)
}
