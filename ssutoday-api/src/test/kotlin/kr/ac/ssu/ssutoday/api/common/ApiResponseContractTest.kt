package kr.ac.ssu.ssutoday.api.common

import kr.ac.ssu.ssutoday.api.article.ArticleController
import kr.ac.ssu.ssutoday.api.common.dto.ApiResponse
import kr.ac.ssu.ssutoday.api.config.JacksonConfig
import kr.ac.ssu.ssutoday.api.device.DeviceController
import kr.ac.ssu.ssutoday.api.reservation.ReservationController
import kr.ac.ssu.ssutoday.api.room.RoomController
import kr.ac.ssu.ssutoday.api.sso.SsoController
import kr.ac.ssu.ssutoday.api.student.StudentController
import kr.ac.ssu.ssutoday.application.reservation.dto.ReservationDetail
import kr.ac.ssu.ssutoday.application.reservation.dto.ReservationRoom
import kr.ac.ssu.ssutoday.application.reservation.dto.RoomReservation
import kr.ac.ssu.ssutoday.application.student.dto.LoginResult
import kr.ac.ssu.ssutoday.core.exception.BusinessException
import kr.ac.ssu.ssutoday.core.status.SsuStatus
import kr.ac.ssu.ssutoday.domain.article.ArticleView
import org.springframework.context.support.ResourceBundleMessageSource
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import tools.jackson.databind.json.JsonMapper
import java.sql.Date
import java.sql.Timestamp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApiResponseContractTest {
    private val messageSource = ResourceBundleMessageSource().apply {
        setBasename("messages")
        setDefaultEncoding("UTF-8")
    }
    private val advice = GlobalControllerAdvice(messageSource)
    private val objectMapper = JsonMapper.builder()
        .also(JacksonConfig().kotlinJsonMapperCustomizer()::customize)
        .build()

    @Test
    fun `성공 응답은 기존 SSU 코드 메시지를 사용한다`() {
        assertEquals(
            ApiResponse("SSU2010", "data", "Login success"),
            apiResponse(SsuStatus.SSU2010, "data", messageSource),
        )
    }

    @Test
    fun `BusinessException은 SSU 코드에 맞는 HTTP 상태와 메시지를 반환한다`() {
        val badRequest = advice.business(BusinessException(SsuStatus.SSU4080))
        val serverError = advice.business(BusinessException(SsuStatus.SSU5090))

        assertEquals(HttpStatus.BAD_REQUEST, badRequest.statusCode)
        assertEquals("Not an existing article", badRequest.body?.message)
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, serverError.statusCode)
        assertEquals("Failed to request reserve", serverError.body?.message)
    }

    @Test
    fun `article 응답 PK는 id가 아니라 idx다`() {
        val json = objectMapper.writeValueAsString(
            ArticleView(
                idx = 1L,
                provider = "cse",
                articleNo = "10",
                title = "title",
                content = "content",
                url = "https://example.com",
                createdAt = Timestamp.valueOf("2026-06-22 12:00:00"),
                updatedAt = null,
            ),
        )

        assertTrue(json.contains("\"idx\":1"))
        assertFalse(json.contains("\"id\":"))
    }

    @Test
    fun `reservation 응답은 기존 필드명을 유지한다`() {
        val json = objectMapper.writeValueAsString(
            ReservationDetail(
                idx = 1L,
                roomNo = "A101",
                date = Date.valueOf("2026-06-22"),
                startBlock = 12,
                endBlock = 13,
                createdAt = Timestamp.valueOf("2026-06-22 12:00:00"),
                deletedAt = null,
                deletedReason = null,
                roomByRoomNo = ReservationRoom(
                    no = "A101",
                    name = "room",
                    major = "[\"cse\"]",
                    capacity = 10,
                    location = "location",
                    tags = "tags",
                    image = "image",
                    bigImage = "bigImage",
                    isAvailable = 1,
                ),
                verifyPhotosByIdx = emptyList(),
                isContinuous = false,
            ),
        )

        assertTrue(json.contains("\"idx\":1"))
        assertTrue(json.contains("\"roomByRoomNo\""))
        assertTrue(json.contains("\"verifyPhotosByIdx\""))
        assertTrue(json.contains("\"isContinuous\":false"))
        assertFalse(json.contains("\"studentId\""))
        assertFalse(json.contains("\"active\""))
    }

    @Test
    fun `기존 서버의 API 경로를 모두 동일하게 제공한다`() {
        val paths = listOf(
            StudentController::class.java,
            DeviceController::class.java,
            ArticleController::class.java,
            ReservationController::class.java,
            RoomController::class.java,
            SsoController::class.java,
        ).flatMap { controller ->
            val basePath = controller.getAnnotation(RequestMapping::class.java).value.single()
            controller.declaredMethods.mapNotNull {
                it.getAnnotation(PostMapping::class.java)?.value?.singleOrNull()?.let(basePath::plus)
            }
        }.toSet()

        assertEquals(
            setOf(
                "/student/login",
                "/student/profile",
                "/student/logout",
                "/student/updateXnApiToken",
                "/student/enrollBiometricsKey",
                "/device/register",
                "/device/unregister",
                "/device/checkVersion",
                "/device/getOption",
                "/device/updateOption",
                "/article/list",
                "/article/get",
                "/reserve/request",
                "/reserve/status",
                "/reserve/list",
                "/reserve/cancel",
                "/reserve/verifyPhoto/upload",
                "/reserve/adminTools",
                "/reserve/done",
                "/room/get",
                "/room/list",
                "/sso/generateToken",
                "/sso/validateToken",
            ),
            paths,
        )
    }

    @Test
    fun `예약 완료 상태 코드 메시지를 기존과 동일하게 반환한다`() {
        assertEquals(
            ApiResponse<Nothing>("SSU2230", null, "Done reservation success"),
            apiResponse(SsuStatus.SSU2230, null, messageSource),
        )
    }

    @Test
    fun `모든 API 성공 응답은 GlobalControllerAdvice가 처리한다`() {
        val missing = listOf(
            StudentController::class.java,
            DeviceController::class.java,
            ArticleController::class.java,
            ReservationController::class.java,
            RoomController::class.java,
            SsoController::class.java,
        ).flatMap { controller ->
            controller.declaredMethods
                .filter { it.getAnnotation(PostMapping::class.java) != null }
                .filter { it.getAnnotation(SsuResponse::class.java) == null }
                .map { "${controller.simpleName}.${it.name}" }
        }

        assertTrue(missing.isEmpty(), "SsuResponse가 없는 API: $missing")
    }

    @Test
    fun `Kotlin is 프로퍼티는 기존 JSON 필드명을 유지한다`() {
        val loginJson = objectMapper.writeValueAsString(
            LoginResult("access", "refresh", 20260000, "name", "cse", true),
        )
        val reservationJson = objectMapper.writeValueAsString(
            RoomReservation(1L, "name", 12, 13, true),
        )

        assertTrue(loginJson.contains("\"isAdmin\":true"))
        assertTrue(reservationJson.contains("\"isMine\":true"))
        assertFalse(loginJson.contains("\"admin\":"))
        assertFalse(reservationJson.contains("\"mine\":"))
    }
}
