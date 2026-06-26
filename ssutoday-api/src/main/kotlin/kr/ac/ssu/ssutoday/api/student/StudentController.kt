package kr.ac.ssu.ssutoday.api.student

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import kr.ac.ssu.ssutoday.api.common.ResponseStatus
import kr.ac.ssu.ssutoday.api.config.LoginStudent
import kr.ac.ssu.ssutoday.api.config.TokenCookieWriter
import kr.ac.ssu.ssutoday.api.config.readCookie
import kr.ac.ssu.ssutoday.api.student.dto.StudentLoginRequest
import kr.ac.ssu.ssutoday.api.student.dto.StudentProfileResponse
import kr.ac.ssu.ssutoday.api.student.dto.StudentUpdateTokenRequest
import kr.ac.ssu.ssutoday.application.student.StudentApplicationService
import kr.ac.ssu.ssutoday.core.exception.BusinessException
import kr.ac.ssu.ssutoday.core.status.StatusCode
import kr.ac.ssu.ssutoday.domain.student.StudentView
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/student")
class StudentController(
    private val studentApplicationService: StudentApplicationService,
    private val tokenCookieWriter: TokenCookieWriter,
) {
    @PostMapping("/login")
    @ResponseStatus(StatusCode.SSU2010)
    fun login(
        @Valid @RequestBody request: StudentLoginRequest,
        httpRequest: HttpServletRequest,
        response: HttpServletResponse,
    ): StudentProfileResponse {
        val result = studentApplicationService.login(request.sToken, request.sIdno)
        val persist = httpRequest.getHeader("User-Agent")?.startsWith("SSUTODAY") == true || request.persistLogin
        tokenCookieWriter.writeAuthCookies(response, result.accessToken, result.refreshToken, persist)
        return StudentProfileResponse(result.studentId, result.name, result.major, result.isAdmin)
    }

    @PostMapping("/profile")
    @ResponseStatus(StatusCode.SSU2020)
    fun getProfile(
        @LoginStudent student: StudentView,
    ) = StudentProfileResponse(student.id, student.name, student.major, student.isAdmin)

    @PostMapping("/logout")
    @ResponseStatus(StatusCode.SSU2030)
    fun logout(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        val refreshToken =
            request.readCookie(TokenCookieWriter.REFRESH_TOKEN_COOKIE)
                ?: throw BusinessException(StatusCode.SSU4000)
        studentApplicationService.logout(refreshToken)
        tokenCookieWriter.clearAuthCookies(response)
    }

    @PostMapping("/updateXnApiToken")
    @ResponseStatus(StatusCode.SSU2190)
    fun updateXnApiToken(
        @LoginStudent student: StudentView,
        @Valid @RequestBody request: StudentUpdateTokenRequest,
    ) {
        studentApplicationService.updateXnApiToken(student.id, request.xnApiToken)
    }
}
