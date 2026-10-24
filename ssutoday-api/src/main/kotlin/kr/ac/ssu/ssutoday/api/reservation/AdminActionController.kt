package kr.ac.ssu.ssutoday.api.reservation

import kr.ac.ssu.ssutoday.api.reservation.AdminActionHtml.escapeHtml
import kr.ac.ssu.ssutoday.application.penalty.PenaltyCommandApplicationService
import kr.ac.ssu.ssutoday.application.penalty.PenaltyQueryApplicationService
import kr.ac.ssu.ssutoday.application.penalty.dto.GrantPenaltyCommand
import kr.ac.ssu.ssutoday.application.penalty.dto.RevokePenaltyCommand
import kr.ac.ssu.ssutoday.application.reservation.ReservationCommandApplicationService
import kr.ac.ssu.ssutoday.core.penalty.PenaltyReason
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/action")
class AdminActionController(
    private val reservationCommandApplicationService: ReservationCommandApplicationService,
    private val penaltyCommandApplicationService: PenaltyCommandApplicationService,
    private val penaltyQueryApplicationService: PenaltyQueryApplicationService,
) {
    @GetMapping(produces = [MediaType.TEXT_HTML_VALUE])
    fun actionPage(
        @RequestParam token: String,
        @RequestParam action: String,
    ): ResponseEntity<String> {
        val html =
            when (action) {
                AdminPenaltyPages.GRANT ->
                    penaltyQueryApplicationService.getGrantForm(token)?.let { AdminPenaltyPages.grantForm(token, it) }
                        ?: AdminPenaltyPages.notFound(action)
                AdminPenaltyPages.REVOKE ->
                    penaltyQueryApplicationService.getRevokeForm(token)?.let { AdminPenaltyPages.revokeForm(token, it) }
                        ?: AdminPenaltyPages.notFound(action)
                else -> buildConfirmHtml(token, action)
            }
        return ResponseEntity.ok(html)
    }

    @PostMapping(produces = [MediaType.TEXT_HTML_VALUE])
    fun executeAction(
        @RequestParam token: String,
        @RequestParam action: String,
        @RequestParam(required = false) reason: String?,
        @RequestParam(required = false) adminName: String?,
        @RequestParam(required = false) penaltyReason: String?,
        @RequestParam(required = false) cancelUpcoming: String?,
    ): ResponseEntity<String> {
        if (action == AdminPenaltyPages.GRANT) {
            val result =
                penaltyCommandApplicationService.grantByAdmin(
                    GrantPenaltyCommand(
                        adminToken = token,
                        reason = PenaltyReason.entries.firstOrNull { it.name == penaltyReason },
                        adminName = adminName,
                        cancelUpcoming = cancelUpcoming != null,
                    ),
                )
            return ResponseEntity.ok(AdminPenaltyPages.result(token, action, result.outcome, result.details))
        }
        if (action == AdminPenaltyPages.REVOKE) {
            val result = penaltyCommandApplicationService.revokeByAdmin(RevokePenaltyCommand(token, reason, adminName))
            return ResponseEntity.ok(AdminPenaltyPages.result(token, action, result.outcome, result.details))
        }

        val result = reservationCommandApplicationService.executeAdminActionByToken(token, action, reason, adminName)
        val (title, message, success) =
            when (result) {
                0 -> Triple("오류", "예약을 찾을 수 없습니다", false)
                1 -> Triple("오류", "이미 취소된 예약입니다", false)
                2 -> Triple("오류", "이미 종료된 예약입니다", false)
                3 -> Triple("완료", "예약이 취소되었습니다", true)
                4 -> Triple("오류", "인증샷이 존재하지 않습니다", false)
                5 -> Triple("완료", "인증샷이 삭제되었습니다", true)
                99 ->
                    if (action == RESERVE_CANCEL) {
                        Triple("오류", "취소 사유를 입력해주세요", false)
                    } else {
                        Triple("오류", "알 수 없는 오류가 발생했습니다", false)
                    }
                else -> Triple("오류", "알 수 없는 오류가 발생했습니다", false)
            }
        val body = AdminActionHtml.resultBody(title, message, success, AdminActionHtml.actionUrl(token, action))
        return ResponseEntity.ok(AdminActionHtml.page(title, body))
    }

    private fun buildConfirmHtml(
        token: String,
        action: String,
    ): String {
        val isCancel = action == RESERVE_CANCEL
        val title = if (isCancel) "관리자 취소" else "인증샷 삭제"
        val description =
            if (isCancel) {
                "예약 취소 사유를 입력한 뒤 한 번 더 확인해주세요."
            } else {
                "삭제 후에는 해당 인증샷을 복구할 수 없습니다. 다시 한 번 확인해주세요."
            }
        val textarea =
            if (isCancel) {
                """
                <label class="field" for="reason">
                  <span>취소 사유</span>
                  <textarea id="reason" name="reason" maxlength="120" placeholder="예: 이용 규칙 위반으로 관리자 취소" required></textarea>
                </label>
                """.trimIndent()
            } else {
                ""
            }
        val buttonLabel = if (isCancel) "사유 확인 후 예약 취소" else "인증샷 삭제 실행"
        val adminNameInput =
            """
            <label class="field" for="adminName">
              <span>처리자</span>
              <input id="adminName" name="adminName" maxlength="30" placeholder="예: 홍길동" required>
            </label>
            """.trimIndent()
        val dangerClass = if (isCancel) " danger" else ""

        return AdminActionHtml.page(
            title,
            """
            <div class="badge">관리자 작업</div>
            <h1>$title</h1>
            <p class="description">$description</p>
            <form method="post" action="/admin/action" class="form">
              <input type="hidden" name="token" value="${escapeHtml(token)}">
              <input type="hidden" name="action" value="${escapeHtml(action)}">
              $adminNameInput
              $textarea
              <button class="primary$dangerClass" type="submit">$buttonLabel</button>
            </form>
            """.trimIndent(),
        )
    }

    private companion object {
        const val RESERVE_CANCEL = "reserveCancel"
    }
}
