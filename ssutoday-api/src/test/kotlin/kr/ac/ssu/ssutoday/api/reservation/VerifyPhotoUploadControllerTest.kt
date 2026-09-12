package kr.ac.ssu.ssutoday.api.reservation

import kr.ac.ssu.ssutoday.api.common.GlobalControllerAdvice
import kr.ac.ssu.ssutoday.api.config.JacksonConfig
import kr.ac.ssu.ssutoday.api.config.LoginStudentArgumentResolver
import kr.ac.ssu.ssutoday.application.attest.dto.PhotoAttestationEvidence
import kr.ac.ssu.ssutoday.application.reservation.ReservationCommandApplicationService
import kr.ac.ssu.ssutoday.application.reservation.ReservationQueryApplicationService
import kr.ac.ssu.ssutoday.application.reservation.VerifyPhotoApplicationService
import kr.ac.ssu.ssutoday.application.reservation.dto.UploadPhotoCommand
import kr.ac.ssu.ssutoday.core.exception.BusinessException
import kr.ac.ssu.ssutoday.core.status.StatusCode
import kr.ac.ssu.ssutoday.domain.student.StudentView
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.context.support.ResourceBundleMessageSource
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import tools.jackson.databind.json.JsonMapper
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class VerifyPhotoUploadControllerTest {
    private val service = mock(VerifyPhotoApplicationService::class.java)
    private val student = StudentView(20260000, "student", "cse", false)
    private val mapper = JsonMapper.builder().also(JacksonConfig().kotlinJsonMapperCustomizer()::customize).build()
    private val messages =
        ResourceBundleMessageSource().apply {
            setBasename("messages")
            setDefaultEncoding("UTF-8")
        }
    private val mvc =
        MockMvcBuilders
            .standaloneSetup(
                ReservationController(
                    mock(ReservationCommandApplicationService::class.java),
                    mock(ReservationQueryApplicationService::class.java),
                    service,
                ),
            ).setCustomArgumentResolvers(LoginStudentArgumentResolver())
            .setControllerAdvice(GlobalControllerAdvice(messages))
            .setMessageConverters(JacksonJsonHttpMessageConverter(mapper))
            .build()
    private val photo = "camera-photo".toByteArray()
    private val request =
        multipart("/reserve/verifyPhoto/upload")
            .file(MockMultipartFile("file", "photo.jpeg", "image/jpeg", photo))
            .param("idx", "42")
            .param("turnstileToken", "turnstile")

    @Test
    fun `구버전 multipart는 nullable 기본값으로 계속 업로드된다`() {
        expectCommand(PhotoAttestationEvidence())
        mvc
            .perform(request.principal(UsernamePasswordAuthenticationToken(student, null)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.statusCode").value("SSU2200"))
    }

    @Test
    fun `증명 필드를 전달하고 학생 ID와 사진 해시는 클라이언트 값으로 덮어쓰지 않는다`() {
        expectCommand(PhotoAttestationEvidence("android", "challenge", "token"))
        mvc
            .perform(
                request
                    .principal(UsernamePasswordAuthenticationToken(student, null))
                    .param("platform", "android")
                    .param("challenge", "challenge")
                    .param("attestation", "token")
                    .param("studentId", "99999999")
                    .param("photoSha256", "forged-hash")
                    .param("clientData", "forged-data"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.statusCode").value("SSU2200"))
    }

    @Test
    fun `인증되지 않은 업로드는 application을 호출하지 않는다`() {
        mvc
            .perform(request)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.statusCode").value("SSU4001"))
        verifyNoInteractions(service)
    }

    @Test
    fun `강제 모드의 검증 거부는 기존 응답 형식으로 반환한다`() {
        doAnswer { throw BusinessException(StatusCode.SSU4206) }.`when`(service).upload(anyCommand())
        mvc
            .perform(request.principal(UsernamePasswordAuthenticationToken(student, null)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.statusCode").value("SSU4206"))
            .andExpect(jsonPath("$.message").value("앱 무결성 인증에 실패했습니다. 다시 촬영해 주세요"))
    }

    private fun expectCommand(evidence: PhotoAttestationEvidence) {
        doAnswer { invocation ->
            val command = invocation.getArgument<UploadPhotoCommand>(0)
            assertEquals(student.id, command.studentId)
            assertEquals(42L, command.reservationId)
            assertEquals(evidence, command.attestation)
            assertContentEquals(photo, command.input.readBytes())
            "https://storage/photo.jpeg"
        }.`when`(service).upload(anyCommand())
    }

    private fun anyCommand(): UploadPhotoCommand =
        any<UploadPhotoCommand>() ?: UploadPhotoCommand("", 1, 1, null, 0, InputStream.nullInputStream())
}
