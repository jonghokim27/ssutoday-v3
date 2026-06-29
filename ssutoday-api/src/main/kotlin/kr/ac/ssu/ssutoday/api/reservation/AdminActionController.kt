package kr.ac.ssu.ssutoday.api.reservation

import kr.ac.ssu.ssutoday.application.reservation.ReservationCommandApplicationService
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
) {
    @GetMapping(produces = [MediaType.TEXT_HTML_VALUE])
    fun actionPage(
        @RequestParam token: String,
        @RequestParam action: String,
    ): ResponseEntity<String> = ResponseEntity.ok(buildConfirmHtml(token, action))

    @PostMapping(produces = [MediaType.TEXT_HTML_VALUE])
    fun executeAction(
        @RequestParam token: String,
        @RequestParam action: String,
        @RequestParam(required = false) reason: String?,
        @RequestParam(required = false) adminName: String?,
    ): ResponseEntity<String> {
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
        return ResponseEntity.ok(buildResultHtml(title, message, success, token, action))
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

        return """
        <!DOCTYPE html>
        <html lang="ko">
          <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>$title</title>
            ${styleBlock()}
          </head>
          <body>
            <main class="shell">
              <section class="panel">
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
              </section>
            </main>
          </body>
        </html>
        """.trimIndent()
    }

    private fun buildResultHtml(
        title: String,
        message: String,
        success: Boolean,
        token: String,
        action: String,
    ): String {
        val badge = if (success) "처리 완료" else "다시 확인 필요"
        val toneClass = if (success) "success" else "danger"
        val retryButton =
            if (success) {
                ""
            } else {
                """<a class="secondary" href="/admin/action?token=${escapeUrl(token)}&action=${escapeUrl(action)}">다시 시도</a>"""
            }

        return """
        <!DOCTYPE html>
        <html lang="ko">
          <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>${escapeHtml(title)}</title>
            ${styleBlock()}
          </head>
          <body>
            <main class="shell">
              <section class="panel">
                <div class="badge $toneClass">$badge</div>
                <h1>${escapeHtml(title)}</h1>
                <p class="description">${escapeHtml(message)}</p>
                <div class="actions">
                  $retryButton
                </div>
              </section>
            </main>
          </body>
        </html>
        """.trimIndent()
    }

    private fun styleBlock(): String =
        """
        <style>
          :root{
            color-scheme: light;
            --color-text-primary:#0f1222;
            --color-text-secondary:#4f5566;
            --color-surface-base:#ffffff;
            --color-surface-subtle:#f8f9fc;
            --color-surface-panel:#f7f8fb;
            --color-border-default:#edeef4;
            --color-border-soft:#f1f2f7;
            --color-brand-gradient:linear-gradient(135deg,#4f7cff,#9b5cff);
            --color-success:#1fb97a;
            --color-success-bg:#e8faf1;
            --color-danger:#ff4d63;
            --color-danger-soft:#ffecef;
            --shadow-card:0 10px 26px -20px rgba(40,30,90,.35);
            --shadow-primary-button:0 16px 32px -12px rgba(106,76,255,.6);
            --font-family-base:Pretendard,-apple-system,BlinkMacSystemFont,system-ui,sans-serif;
          }
          *{box-sizing:border-box}
          body{
            margin:0;
            min-height:100vh;
            font-family:var(--font-family-base);
            color:var(--color-text-primary);
            background:
              radial-gradient(circle at top,#eef3ff 0,#f8f9fc 38%,#f8f9fc 100%);
          }
          .shell{
            min-height:100vh;
            display:flex;
            align-items:center;
            justify-content:center;
            padding:24px;
          }
          .panel{
            width:min(100%,420px);
            padding:28px 24px 24px;
            border-radius:22px;
            background:rgba(255,255,255,.92);
            border:1px solid var(--color-border-soft);
            box-shadow:var(--shadow-card);
            backdrop-filter:blur(16px);
            animation:floatUp .22s ease-out;
          }
          .badge{
            display:inline-flex;
            align-items:center;
            padding:7px 12px;
            border-radius:999px;
            background:rgba(79,124,255,.1);
            color:#315fdb;
            font-size:12px;
            font-weight:700;
          }
          .badge.success{
            background:var(--color-success-bg);
            color:var(--color-success);
          }
          .badge.danger{
            background:var(--color-danger-soft);
            color:var(--color-danger);
          }
          h1{
            margin:16px 0 10px;
            font-size:24px;
            line-height:1.25;
          }
          .description{
            margin:0 0 22px;
            color:var(--color-text-secondary);
            font-size:14px;
            line-height:1.6;
          }
          .form{
            display:grid;
            gap:16px;
          }
          .field{
            display:grid;
            gap:8px;
          }
          .field span{
            font-size:13px;
            font-weight:700;
            color:var(--color-text-primary);
          }
          input,textarea{
            width:100%;
            padding:14px 15px;
            border:1px solid var(--color-border-default);
            border-radius:18px;
            background:var(--color-surface-panel);
            color:var(--color-text-primary);
            resize:vertical;
            outline:none;
            font-size:14px;
            line-height:1.5;
          }
          input{
            min-height:52px;
          }
          textarea{
            min-height:116px;
          }
          input:focus,textarea:focus{
            border-color:#7b89ff;
            box-shadow:0 0 0 4px rgba(79,124,255,.12);
          }
          .primary,.secondary{
            display:inline-flex;
            justify-content:center;
            align-items:center;
            min-height:52px;
            width:100%;
            border:none;
            border-radius:18px;
            text-decoration:none;
            font-size:15px;
            font-weight:700;
          }
          .primary{
            color:#fff;
            background:var(--color-brand-gradient);
            box-shadow:var(--shadow-primary-button);
          }
          .primary.danger{
            background:linear-gradient(135deg,#ff6b6b,#ff4d63);
            box-shadow:0 16px 32px -12px rgba(255,77,99,.55);
          }
          .secondary{
            color:var(--color-text-primary);
            background:var(--color-surface-panel);
            border:1px solid var(--color-border-default);
          }
          .actions{
            display:grid;
            gap:12px;
            margin-top:22px;
          }
          @keyframes floatUp{
            from{opacity:0;transform:translateY(14px)}
            to{opacity:1;transform:translateY(0)}
          }
        </style>
        """.trimIndent()

    private fun escapeHtml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")

    private fun escapeUrl(value: String): String = java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8)

    private companion object {
        const val RESERVE_CANCEL = "reserveCancel"
    }
}
