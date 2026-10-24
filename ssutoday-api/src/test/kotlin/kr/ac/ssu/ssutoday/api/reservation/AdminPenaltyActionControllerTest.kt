package kr.ac.ssu.ssutoday.api.reservation

import kr.ac.ssu.ssutoday.application.penalty.PenaltyCommandApplicationService
import kr.ac.ssu.ssutoday.application.penalty.PenaltyQueryApplicationService
import kr.ac.ssu.ssutoday.application.penalty.dto.GrantPenaltyCommand
import kr.ac.ssu.ssutoday.application.penalty.dto.PenaltyActionOutcome
import kr.ac.ssu.ssutoday.application.penalty.dto.PenaltyActionResult
import kr.ac.ssu.ssutoday.application.penalty.dto.PenaltyGrantForm
import kr.ac.ssu.ssutoday.application.penalty.dto.PenaltyGrantOption
import kr.ac.ssu.ssutoday.application.penalty.dto.RevokePenaltyCommand
import kr.ac.ssu.ssutoday.application.reservation.ReservationCommandApplicationService
import kr.ac.ssu.ssutoday.core.penalty.PenaltyReason
import kr.ac.ssu.ssutoday.domain.penalty.PenaltyGrantRejection
import kr.ac.ssu.ssutoday.domain.penalty.PenaltyStanding
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.http.converter.StringHttpMessageConverter
import org.springframework.test.web.servlet.RequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdminPenaltyActionControllerTest {
    private val commands = mock(PenaltyCommandApplicationService::class.java)
    private val queries = mock(PenaltyQueryApplicationService::class.java)
    private val mvc =
        MockMvcBuilders
            .standaloneSetup(AdminActionController(mock(ReservationCommandApplicationService::class.java), commands, queries))
            .setMessageConverters(StringHttpMessageConverter(Charsets.UTF_8))
            .build()
    private val clean = PenaltyStanding(0, 6, null, false, null)
    private val until = LocalDateTime.of(2027, 2, 8, 15, 12)

    @Test
    fun `부여 화면은 예약 정보를 이스케이프하고 부여할 수 없는 종류를 막는다`() {
        `when`(queries.getGrantForm("token")).thenReturn(
            PenaltyGrantForm(
                details = listOf("예약자" to "<b>홍길동</b> (20260000/컴)"),
                photoUrl = null,
                current = clean,
                photo =
                    PenaltyGrantOption(
                        PenaltyGrantRejection.DEADLINE_PASSED,
                        clean.copy(photoCount = 1),
                        LocalDateTime.of(2026, 11, 10, 12, 0),
                    ),
                fraud = PenaltyGrantOption(null, PenaltyStanding(0, null, until, true, null), null),
                upcomingReservations = listOf("2026년 11월 12일(목) 14:00 ~ 16:00 스터디룸 1"),
            ),
        )

        val html = page(get("/admin/action").param("token", "token").param("action", "penaltyGrant"))

        assertTrue("&lt;b&gt;홍길동&lt;/b&gt;" in html)
        assertFalse("<b>홍길동</b>" in html)
        assertTrue("""value="PHOTO" disabled""" in html)
        assertTrue("이용 종료 후 7일이 지나 부여할 수 없습니다" in html)
        assertTrue("처리 후: 예약 정지 (2027년 02월 08일(월) 15:12까지)" in html)
        assertTrue("이용 전 다른 예약 1건도 함께 취소" in html)
        PenaltyReason.entries.filter { it.adminSelectable }.forEach { assertTrue("""value="${it.name}"""" in html, it.name) }
        assertFalse("""value="PHOTO_MISSING"""" in html)
    }

    @Test
    fun `부여 요청은 선택한 사유와 일괄 취소 여부를 넘기고 결과를 보여준다`() {
        `when`(commands.grantByAdmin(GrantPenaltyCommand("token", PenaltyReason.FRAUD_AUTOMATION, "김관리", true))).thenReturn(
            PenaltyActionResult(PenaltyActionOutcome.DONE, listOf("취소된 예약" to "2건")),
        )
        `when`(commands.grantByAdmin(GrantPenaltyCommand("token", null, "김관리", false))).thenReturn(
            PenaltyActionResult(PenaltyActionOutcome.INVALID_INPUT),
        )

        val done =
            page(
                post("/admin/action")
                    .param("token", "token")
                    .param("action", "penaltyGrant")
                    .param("penaltyReason", "FRAUD_AUTOMATION")
                    .param("cancelUpcoming", "on")
                    .param("adminName", "김관리"),
            )
        assertTrue("페널티가 부여되었습니다" in done)
        assertTrue("<dt>취소된 예약</dt><dd>2건</dd>" in done)

        val invalid =
            page(
                post("/admin/action")
                    .param("token", "token")
                    .param("action", "penaltyGrant")
                    .param("penaltyReason", "UNKNOWN")
                    .param("adminName", "김관리"),
            )
        assertTrue("페널티 종류와 사유, 처리자를 입력해주세요" in invalid)
        assertTrue("다시 시도" in invalid)
    }

    @Test
    fun `취소 요청은 사유와 처리자를 넘기고 없는 토큰은 오류를 보여준다`() {
        `when`(commands.revokeByAdmin(RevokePenaltyCommand("penalty-token", "이의제기 인용", "김관리"))).thenReturn(
            PenaltyActionResult(PenaltyActionOutcome.ALREADY_REVOKED),
        )

        val revoked =
            page(
                post("/admin/action")
                    .param("token", "penalty-token")
                    .param("action", "penaltyRevoke")
                    .param("reason", "이의제기 인용")
                    .param("adminName", "김관리"),
            )
        assertTrue("이미 취소된 페널티입니다" in revoked)
        assertTrue("페널티를 찾을 수 없습니다" in page(get("/admin/action").param("token", "unknown").param("action", "penaltyRevoke")))
    }

    private fun page(request: RequestBuilder): String =
        mvc
            .perform(request)
            .andReturn()
            .response
            .getContentAsString(Charsets.UTF_8)
}
