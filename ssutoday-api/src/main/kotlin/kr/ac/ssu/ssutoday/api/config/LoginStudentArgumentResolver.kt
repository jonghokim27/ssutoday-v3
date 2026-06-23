package kr.ac.ssu.ssutoday.api.config

import kr.ac.ssu.ssutoday.core.exception.BusinessException
import kr.ac.ssu.ssutoday.core.status.StatusCode
import kr.ac.ssu.ssutoday.domain.student.StudentView
import org.springframework.core.MethodParameter
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

@Component
class LoginStudentArgumentResolver : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.hasParameterAnnotation(LoginStudent::class.java) &&
            parameter.parameterType == StudentView::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): StudentView =
        (webRequest.userPrincipal as? Authentication)
            ?.principal
            .let { it as? StudentView }
            ?: throw BusinessException(StatusCode.SSU4001)
}
