package kr.ac.ssu.ssutoday.api.student

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import kr.ac.ssu.ssutoday.api.common.ResponseStatus
import kr.ac.ssu.ssutoday.api.config.LoginStudent
import kr.ac.ssu.ssutoday.api.student.dto.BiometricsKeyRequest
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
) {
    @PostMapping("/login")
    @ResponseStatus(StatusCode.SSU2010)
    fun login(
        @Valid @RequestBody request: StudentLoginRequest,
    ) = studentApplicationService.login(request.sToken, request.sIdno)

    @PostMapping("/profile")
    @ResponseStatus(StatusCode.SSU2020)
    fun getProfile(
        @LoginStudent student: StudentView,
    ) = StudentProfileResponse(student.id, student.name, student.major, student.isAdmin)

    @PostMapping("/logout")
    @ResponseStatus(StatusCode.SSU2030)
    fun logout(request: HttpServletRequest) {
        val refreshToken =
            request.getHeader("Refresh-Token")
                ?: throw BusinessException(StatusCode.SSU4000)
        studentApplicationService.logout(refreshToken)
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
