package kr.ac.ssu.ssutoday.api.config

import kr.ac.ssu.ssutoday.core.exception.BusinessException
import kr.ac.ssu.ssutoday.core.status.StatusCode
import kr.ac.ssu.ssutoday.domain.student.StudentView
import org.springframework.core.MethodParameter
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.web.context.request.ServletWebRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LoginStudentArgumentResolverTest {
    private val resolver = LoginStudentArgumentResolver()
    private val parameter = MethodParameter(
        Fixture::class.java.getDeclaredMethod("handle", StudentView::class.java),
        0,
    )

    @Test
    fun `인증 principal을 StudentView 파라미터로 주입한다`() {
        val student = StudentView(20260000, "student", "cse", false)
        val request = MockHttpServletRequest().apply {
            userPrincipal = UsernamePasswordAuthenticationToken(student, null)
        }

        assertTrue(resolver.supportsParameter(parameter))
        assertEquals(
            student,
            resolver.resolveArgument(parameter, null, ServletWebRequest(request), null),
        )
    }

    @Test
    fun `인증 principal이 없으면 SSU4001을 반환한다`() {
        val exception = assertFailsWith<BusinessException> {
            resolver.resolveArgument(
                parameter,
                null,
                ServletWebRequest(MockHttpServletRequest()),
                null,
            )
        }

        assertEquals(StatusCode.SSU4001, exception.status)
    }

    @Suppress("unused")
    private class Fixture {
        fun handle(@LoginStudent student: StudentView) = student
    }
}
