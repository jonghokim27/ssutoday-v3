package kr.ac.ssu.ssutoday.api.reservation

import kr.ac.ssu.ssutoday.api.common.GlobalControllerAdvice
import kr.ac.ssu.ssutoday.api.config.JacksonConfig
import kr.ac.ssu.ssutoday.api.config.LoginStudentArgumentResolver
import kr.ac.ssu.ssutoday.application.penalty.PenaltyQueryApplicationService
import kr.ac.ssu.ssutoday.application.reservation.ReservationCommandApplicationService
import kr.ac.ssu.ssutoday.application.reservation.ReservationQueryApplicationService
import kr.ac.ssu.ssutoday.application.reservation.VerifyPhotoApplicationService
import kr.ac.ssu.ssutoday.domain.student.StudentView
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.context.support.ResourceBundleMessageSource
import org.springframework.http.MediaType
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import tools.jackson.databind.json.JsonMapper
import kotlin.test.Test

class AdminPenaltyPageControllerTest {
    private val penalties = mock(PenaltyQueryApplicationService::class.java)
    private val mapper = JsonMapper.builder().also(JacksonConfig().kotlinJsonMapperCustomizer()::customize).build()
    private val mvc =
        MockMvcBuilders
            .standaloneSetup(
                ReservationController(
                    mock(ReservationCommandApplicationService::class.java),
                    mock(ReservationQueryApplicationService::class.java),
                    mock(VerifyPhotoApplicationService::class.java),
                    penalties,
                ),
            ).setCustomArgumentResolvers(LoginStudentArgumentResolver())
            .setControllerAdvice(
                GlobalControllerAdvice(
                    ResourceBundleMessageSource().apply {
                        setBasename("messages")
                        setDefaultEncoding("UTF-8")
                    },
                ),
            ).setMessageConverters(JacksonJsonHttpMessageConverter(mapper))
            .build()

    @Test
    fun `관리자에게 예약의 페널티 부여 화면 경로를 돌려준다`() {
        `when`(penalties.getGrantToken(42)).thenReturn("a+b/c")

        mvc
            .perform(request(StudentView(20200000, "관리자", "cse", true)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.statusCode").value("SSU2260"))
            .andExpect(jsonPath("$.data.path").value("/admin/action?token=a%2Bb%2Fc&action=penaltyGrant"))
    }

    @Test
    fun `관리자가 아니면 토큰을 조회하지 않고 거부한다`() {
        mvc
            .perform(request(StudentView(20260000, "학생", "cse", false)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.statusCode").value("SSU4003"))
        verifyNoInteractions(penalties)
    }

    private fun request(student: StudentView) =
        post("/reserve/adminPenalty")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"idx":42}""")
            .principal(UsernamePasswordAuthenticationToken(student, null))
}
