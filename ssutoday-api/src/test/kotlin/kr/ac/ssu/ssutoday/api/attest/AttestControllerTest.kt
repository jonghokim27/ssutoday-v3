package kr.ac.ssu.ssutoday.api.attest

import kr.ac.ssu.ssutoday.api.common.GlobalControllerAdvice
import kr.ac.ssu.ssutoday.api.config.JacksonConfig
import kr.ac.ssu.ssutoday.api.config.LoginStudentArgumentResolver
import kr.ac.ssu.ssutoday.application.attest.AppAttestRegistrationApplicationService
import kr.ac.ssu.ssutoday.application.attest.AttestApplicationService
import kr.ac.ssu.ssutoday.application.attest.dto.AttestChallengeResult
import kr.ac.ssu.ssutoday.application.attest.dto.CreateAttestChallengeCommand
import kr.ac.ssu.ssutoday.application.attest.dto.RegisterAppAttestCommand
import kr.ac.ssu.ssutoday.application.attest.dto.RegisterAppAttestResult
import kr.ac.ssu.ssutoday.core.attestation.AttestationPurpose
import kr.ac.ssu.ssutoday.domain.student.StudentView
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
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
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import kotlin.test.Test

class AttestControllerTest {
    private val service = mock(AttestApplicationService::class.java)
    private val registration = mock(AppAttestRegistrationApplicationService::class.java)
    private val student = StudentView(20260000, "student", "cse", false)
    private val mapper =
        JsonMapper
            .builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .also(JacksonConfig().kotlinJsonMapperCustomizer()::customize)
            .build()
    private val messages =
        ResourceBundleMessageSource().apply {
            setBasename("messages")
            setDefaultEncoding("UTF-8")
        }
    private val mvc =
        MockMvcBuilders
            .standaloneSetup(AttestController(service, registration))
            .setCustomArgumentResolvers(LoginStudentArgumentResolver())
            .setControllerAdvice(GlobalControllerAdvice(messages))
            .setMessageConverters(JacksonJsonHttpMessageConverter(mapper))
            .build()

    @Test
    fun `키 등록도 인증 principal을 사용하고 keyId 확인 응답을 돌려준다`() {
        val keyId = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="
        val challenge = keyId.dropLast(1)
        val command = RegisterAppAttestCommand(student.id, keyId, challenge, "YWJj")
        `when`(registration.register(command)).thenReturn(RegisterAppAttestResult(keyId))
        mvc
            .perform(
                post("/attest/register")
                    .principal(UsernamePasswordAuthenticationToken(student, null))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"keyId":"$keyId","challenge":"$challenge","attestation":"YWJj","studentId":99999999}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.keyId").value(keyId))
        verify(registration).register(command)
    }

    @Test
    fun `키 등록은 미인증과 비어 있거나 과도한 필드를 거부한다`() {
        mvc
            .perform(
                post("/attest/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"keyId":"key","challenge":"challenge","attestation":"YWJj"}"""),
            ).andExpect(jsonPath("$.statusCode").value("SSU4001"))
        mvc
            .perform(
                post("/attest/register")
                    .principal(UsernamePasswordAuthenticationToken(student, null))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"keyId":"","challenge":"challenge","attestation":"YWJj"}"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.statusCode").value("SSU4000"))
        verifyNoInteractions(registration)
    }

    @Test
    fun `학생 ID는 요청 본문 대신 인증 principal에서 가져온다`() {
        val command = CreateAttestChallengeCommand(student.id, AttestationPurpose.APP_ATTEST_REGISTER)
        `when`(service.createChallenge(command))
            .thenReturn(AttestChallengeResult("challenge", 60, student.id, command.purpose, null))

        mvc
            .perform(
                post("/attest/challenge")
                    .principal(UsernamePasswordAuthenticationToken(student, null))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"purpose":"APP_ATTEST_REGISTER","studentId":99999999}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.statusCode").value("SSU2000"))
            .andExpect(jsonPath("$.data.studentId").value(student.id))
            .andExpect(jsonPath("$.data.expiresInSeconds").value(60))
        verify(service).createChallenge(command)
    }

    @Test
    fun `인증 정보가 없으면 challenge를 발급하지 않는다`() {
        mvc
            .perform(
                post("/attest/challenge")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"purpose":"APP_ATTEST_REGISTER"}"""),
            ).andExpect(jsonPath("$.statusCode").value("SSU4001"))
        verifyNoInteractions(service)
    }

    @Test
    fun `알 수 없는 용도와 양수가 아닌 예약은 입력 오류로 응답한다`() {
        listOf(
            """{"purpose":"INVALID"}""",
            """{"purpose":"VERIFY_PHOTO_UPLOAD","reservationId":0}""",
        ).forEach { body ->
            mvc
                .perform(
                    post("/attest/challenge")
                        .principal(UsernamePasswordAuthenticationToken(student, null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body),
                ).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.statusCode").value("SSU4000"))
        }
        verifyNoInteractions(service)
    }
}
