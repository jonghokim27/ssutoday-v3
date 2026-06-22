package kr.ac.ssu.ssutoday.api.sso

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import kr.ac.ssu.ssutoday.api.common.SsuResponse
import kr.ac.ssu.ssutoday.api.config.AuthenticatedStudent
import kr.ac.ssu.ssutoday.api.sso.dto.SsoGenerateRequest
import kr.ac.ssu.ssutoday.api.sso.dto.SsoGenerateResponse
import kr.ac.ssu.ssutoday.api.sso.dto.SsoValidateRequest
import kr.ac.ssu.ssutoday.api.sso.dto.SsoValidateResponse
import kr.ac.ssu.ssutoday.application.sso.SsoApplicationService
import kr.ac.ssu.ssutoday.core.exception.BusinessException
import kr.ac.ssu.ssutoday.core.status.SsuStatus
import kr.ac.ssu.ssutoday.domain.student.StudentView
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/sso")
class SsoController(
    private val ssoApplicationService: SsoApplicationService,
) {
    @PostMapping("/generateToken")
    @SsuResponse(SsuStatus.SSU2150)
    fun generate(
        @AuthenticatedStudent student: StudentView,
        @Valid @RequestBody request: SsoGenerateRequest,
    ): SsoGenerateResponse {
        val result = ssoApplicationService.generate(student, request.clientId)
        return SsoGenerateResponse(result.token, result.callbackUrl)
    }

    @PostMapping("/validateToken")
    @SsuResponse(SsuStatus.SSU2160)
    fun validate(
        @Valid @RequestBody request: SsoValidateRequest,
        servletRequest: HttpServletRequest,
    ): SsoValidateResponse {
        val result = ssoApplicationService.validate(
            servletRequest.getHeader("X-SSUtoday-Client-Id")
                ?: throw BusinessException(SsuStatus.SSU4000),
            servletRequest.getHeader("X-SSUtoday-Client-Secret")
                ?: throw BusinessException(SsuStatus.SSU4000),
            request.ssoToken,
        )
        return SsoValidateResponse(result.studentId, result.name, result.major)
    }
}
