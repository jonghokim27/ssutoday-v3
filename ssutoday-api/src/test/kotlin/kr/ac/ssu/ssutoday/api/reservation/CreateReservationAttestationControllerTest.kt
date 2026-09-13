package kr.ac.ssu.ssutoday.api.reservation

import kr.ac.ssu.ssutoday.api.common.GlobalControllerAdvice
import kr.ac.ssu.ssutoday.api.config.JacksonConfig
import kr.ac.ssu.ssutoday.api.config.LoginStudentArgumentResolver
import kr.ac.ssu.ssutoday.application.attest.dto.AttestationEvidence
import kr.ac.ssu.ssutoday.application.reservation.ReservationCommandApplicationService
import kr.ac.ssu.ssutoday.application.reservation.ReservationQueryApplicationService
import kr.ac.ssu.ssutoday.application.reservation.VerifyPhotoApplicationService
import kr.ac.ssu.ssutoday.application.reservation.dto.CreateReservationCommand
import kr.ac.ssu.ssutoday.domain.student.StudentView
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.context.support.ResourceBundleMessageSource
import org.springframework.http.MediaType
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class CreateReservationAttestationControllerTest {
    private val service = mock(ReservationCommandApplicationService::class.java)
    private val student = StudentView(20260000, "student", "cse", false)
    private val mapper = JsonMapper.builder().also(JacksonConfig().kotlinJsonMapperCustomizer()::customize).build()
    private val mvc =
        MockMvcBuilders
            .standaloneSetup(
                ReservationController(
                    service,
                    mock(ReservationQueryApplicationService::class.java),
                    mock(VerifyPhotoApplicationService::class.java),
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
    private val body = """{"turnstileToken":"turnstile","roomNo":"1","date":"2026-09-14","startBlock":20,"endBlock":23} """.trim()

    @Test
    fun `legacy reservation JSON remains valid with missing attestation`() {
        expect(AttestationEvidence())
        mvc
            .perform(
                post(
                    "/reserve/request",
                ).contentType(MediaType.APPLICATION_JSON).content(body).principal(UsernamePasswordAuthenticationToken(student, null)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.statusCode").value("SSU2090"))
    }

    @Test
    fun `new proof fields are passed through but client student and admin claims are ignored`() {
        expect(AttestationEvidence("android", "challenge", "token"))
        val proof =
            body.dropLast(1) +
                """, "platform":"android", "challenge":"challenge", "attestation":"token", "studentId":99999999, "admin":true, "clientData":"forged"} """
        mvc
            .perform(
                post(
                    "/reserve/request",
                ).contentType(MediaType.APPLICATION_JSON).content(proof).principal(UsernamePasswordAuthenticationToken(student, null)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.statusCode").value("SSU2090"))
    }

    @Test
    fun `unauthenticated reservation cannot reach application`() {
        mvc
            .perform(post("/reserve/request").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.statusCode").value("SSU4001"))
        verifyNoInteractions(service)
    }

    private fun expect(evidence: AttestationEvidence) {
        doAnswer {
            val command = it.getArgument<CreateReservationCommand>(0)
            assertEquals(
                CreateReservationCommand("turnstile", student.id, "cse", false, "1", LocalDate.of(2026, 9, 14), 20, 23, evidence),
                command,
            )
            42L
        }.`when`(service).createReservationRequest(
            any<CreateReservationCommand>() ?: CreateReservationCommand("", 1, "", false, "1", LocalDate.of(2026, 9, 14), 20, 23),
        )
    }
}
