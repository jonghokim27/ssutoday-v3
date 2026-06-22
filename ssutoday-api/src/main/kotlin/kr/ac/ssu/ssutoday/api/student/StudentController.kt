package kr.ac.ssu.ssutoday.api.student

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import kr.ac.ssu.ssutoday.api.common.SsuResponse
import kr.ac.ssu.ssutoday.api.config.AuthenticatedStudent
import kr.ac.ssu.ssutoday.api.student.dto.BiometricsKeyRequest
import kr.ac.ssu.ssutoday.api.student.dto.StudentLoginRequest
import kr.ac.ssu.ssutoday.api.student.dto.StudentProfileResponse
import kr.ac.ssu.ssutoday.api.student.dto.StudentUpdateTokenRequest
import kr.ac.ssu.ssutoday.application.student.StudentApplicationService
import kr.ac.ssu.ssutoday.core.exception.BusinessException
import kr.ac.ssu.ssutoday.core.status.SsuStatus
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
    @SsuResponse(SsuStatus.SSU2010)
    fun login(@Valid @RequestBody request: StudentLoginRequest) =
        studentApplicationService.login(request.sToken, request.sIdno)

    @PostMapping("/profile")
    @SsuResponse(SsuStatus.SSU2020)
    fun profile(@AuthenticatedStudent student: StudentView) =
        StudentProfileResponse(student.id, student.name, student.major, student.isAdmin)

    @PostMapping("/logout")
    @SsuResponse(SsuStatus.SSU2030)
    fun logout(request: HttpServletRequest) {
        val refreshToken = request.getHeader("Refresh-Token")
            ?: throw BusinessException(SsuStatus.SSU4000)
        studentApplicationService.logout(refreshToken)
    }

    @PostMapping("/updateXnApiToken")
    @SsuResponse(SsuStatus.SSU2190)
    fun updateToken(
        @AuthenticatedStudent student: StudentView,
        @Valid @RequestBody request: StudentUpdateTokenRequest,
    ) {
        studentApplicationService.updateXnApiToken(student.id, request.xnApiToken)
    }

    @PostMapping("/enrollBiometricsKey")
    @SsuResponse(SsuStatus.SSU2210)
    fun biometrics(
        @AuthenticatedStudent student: StudentView,
        @Valid @RequestBody request: BiometricsKeyRequest,
    ) {
        studentApplicationService.enrollBiometricsKey(
            student.id,
            request.osType,
            request.uuid,
            request.publicKey,
        )
    }
}
