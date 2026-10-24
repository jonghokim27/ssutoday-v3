package kr.ac.ssu.ssutoday.api.reservation

import kr.ac.ssu.ssutoday.api.reservation.AdminActionHtml.detailList
import kr.ac.ssu.ssutoday.api.reservation.AdminActionHtml.detailRows
import kr.ac.ssu.ssutoday.api.reservation.AdminActionHtml.escapeHtml
import kr.ac.ssu.ssutoday.application.penalty.PenaltyTextFormatter
import kr.ac.ssu.ssutoday.application.penalty.dto.PenaltyActionOutcome
import kr.ac.ssu.ssutoday.application.penalty.dto.PenaltyGrantForm
import kr.ac.ssu.ssutoday.application.penalty.dto.PenaltyGrantOption
import kr.ac.ssu.ssutoday.application.penalty.dto.PenaltyRevokeForm
import kr.ac.ssu.ssutoday.application.reservation.ReservationDateTimeFormatter
import kr.ac.ssu.ssutoday.core.penalty.PenaltyReason
import kr.ac.ssu.ssutoday.core.penalty.PenaltyType
import kr.ac.ssu.ssutoday.domain.penalty.PenaltyGrantRejection
import kr.ac.ssu.ssutoday.domain.penalty.PenaltyStanding

/** Discord의 페널티 부여·취소 버튼으로 여는 관리자 작업 페이지. */
internal object AdminPenaltyPages {
    const val GRANT = "penaltyGrant"
    const val REVOKE = "penaltyRevoke"

    // 버튼을 연달아 눌러 같은 요청을 두 번 보내지 않게 한다. 서버도 예약·페널티 행을 잠가 중복을 막는다.
    private const val PREVENT_RESUBMIT =
        "document.querySelector('form').addEventListener('submit', function () { submit.disabled = true; });"

    fun grantForm(
        token: String,
        form: PenaltyGrantForm,
    ): String {
        val photo = form.photo
        val fraud = form.fraud
        val photoImage = form.photoUrl?.let { """<img class="thumb" src="${escapeHtml(it)}" alt="인증샷">""" } ?: ""
        val upcomingText = form.upcomingReservations.joinToString(" · ", transform = ::escapeHtml)
        val upcoming =
            if (form.upcomingReservations.isEmpty()) {
                ""
            } else {
                """
                <label class="check" data-type="FRAUD">
                  <input type="checkbox" name="cancelUpcoming" checked>
                  <div>이용 전 다른 예약 ${form.upcomingReservations.size}건도 함께 취소<small>$upcomingText</small></div>
                </label>
                """.trimIndent()
            }
        val deadline =
            photo.deadline?.let {
                val text = escapeHtml(ReservationDateTimeFormatter.formatDateTime(it))
                """<p class="note" data-type="PHOTO">부여 가능 기한: ${text}까지 (이용 종료 후 7일)</p>"""
            } ?: ""
        val body =
            """
            <div class="badge">관리자 작업</div>
            <h1>페널티 부여</h1>
            <p class="description">부여 즉시 학생에게 푸시 알림이 발송됩니다. 처리 내용을 한 번 더 확인해주세요.</p>
            <dl class="info">${detailRows(form.details)}$photoImage</dl>
            <div class="change"><span>현재 상태</span><strong>${escapeHtml(short(form.current))}</strong></div>
            <form method="post" action="/admin/action" class="form">
              <input type="hidden" name="token" value="${escapeHtml(token)}">
              <input type="hidden" name="action" value="$GRANT">
              <div class="field">
                <span>페널티 종류</span>
                <div class="options">
                  ${option(PenaltyType.PHOTO, "인증샷 미인정", "페널티 1회 · 부여일로부터 30일간 유효", photo)}
                  ${option(PenaltyType.FRAUD, "부정 예약", "90일간 예약 정지 · 해당 예약 즉시 취소", fraud)}
                </div>
              </div>
              ${reasons(PenaltyType.PHOTO, "부여 사유")}
              ${reasons(PenaltyType.FRAUD, "부정 유형")}
              $upcoming
              <label class="field" for="adminName">
                <span>처리자</span>
                <input id="adminName" name="adminName" maxlength="30" placeholder="예: 홍길동" required>
              </label>
              $deadline
              <button class="primary danger" id="submit" type="submit">페널티 부여</button>
            </form>
            <script>
              (function () {
                var types = document.querySelectorAll('input[name="penaltyType"]');
                var scoped = document.querySelectorAll('[data-type]');
                var submit = document.getElementById('submit');
                function sync() {
                  var checked = document.querySelector('input[name="penaltyType"]:checked');
                  var type = checked ? checked.value : null;
                  scoped.forEach(function (el) { el.hidden = el.dataset.type !== type; });
                  document.querySelectorAll('input[name="penaltyReason"]').forEach(function (input) {
                    if (input.dataset.reasonType !== type) input.checked = false;
                  });
                  var own = document.querySelectorAll('input[name="penaltyReason"][data-reason-type="' + type + '"]');
                  if (own.length === 1) own[0].checked = true;
                  submit.textContent = type === 'FRAUD' ? '예약 정지 처리' : '페널티 부여';
                }
                types.forEach(function (input) { input.addEventListener('change', sync); });
                sync();
                $PREVENT_RESUBMIT
              })();
            </script>
            """.trimIndent()
        return AdminActionHtml.page("페널티 부여", body)
    }

    fun revokeForm(
        token: String,
        form: PenaltyRevokeForm,
    ): String {
        val unavailable = form.unavailable
        val current = escapeHtml(short(form.current))
        val after = escapeHtml(short(form.after))
        val content =
            if (unavailable != null) {
                """<p class="description">${escapeHtml(revokeMessage(unavailable))}</p>"""
            } else {
                """
                <div class="change"><span>처리 결과</span><strong><s>$current</s> → <em>$after</em></strong></div>
                <form method="post" action="/admin/action" class="form">
                  <input type="hidden" name="token" value="${escapeHtml(token)}">
                  <input type="hidden" name="action" value="$REVOKE">
                  <label class="field" for="reason">
                    <span>취소 사유</span>
                    <textarea id="reason" name="reason" maxlength="200" placeholder="예: 이의제기 검토 결과 스터디룸 내부 촬영으로 확인" required></textarea>
                  </label>
                  <label class="field" for="adminName">
                    <span>처리자</span>
                    <input id="adminName" name="adminName" maxlength="30" placeholder="예: 홍길동" required>
                  </label>
                  <button class="primary" id="submit" type="submit">사유 확인 후 페널티 취소</button>
                </form>
                <script>(function () { var submit = document.getElementById('submit'); $PREVENT_RESUBMIT })();</script>
                """.trimIndent()
            }
        val body =
            """
            <div class="badge">관리자 작업</div>
            <h1>페널티 취소</h1>
            <p class="description">취소 즉시 학생에게 푸시 알림이 발송됩니다. 취소 사유는 학생에게 발송되지 않습니다.</p>
            ${detailList(form.details)}
            $content
            """.trimIndent()
        return AdminActionHtml.page("페널티 취소", body)
    }

    fun notFound(action: String): String {
        val message = if (action == GRANT) "예약을 찾을 수 없습니다" else "페널티를 찾을 수 없습니다"
        return AdminActionHtml.page("오류", AdminActionHtml.resultBody("오류", message, success = false, retryUrl = null))
    }

    fun result(
        token: String,
        action: String,
        outcome: PenaltyActionOutcome,
        details: List<Pair<String, String>>,
    ): String {
        val success = outcome == PenaltyActionOutcome.DONE
        val message = if (action == GRANT) grantMessage(outcome) else revokeMessage(outcome)
        val title = if (success) "완료" else "오류"
        val body = AdminActionHtml.resultBody(title, message, success, AdminActionHtml.actionUrl(token, action), details)
        return AdminActionHtml.page(title, body)
    }

    private fun option(
        type: PenaltyType,
        title: String,
        description: String,
        option: PenaltyGrantOption,
    ): String {
        val rejection = option.rejection
        val status =
            if (rejection != null) {
                """<small class="blocked">${escapeHtml(rejectionMessage(rejection))}</small>"""
            } else {
                """<small class="after">처리 후: ${escapeHtml(short(option.after))}</small>"""
            }
        val disabled = if (rejection != null) " disabled" else ""
        return """
            <label class="opt">
              <input type="radio" name="penaltyType" value="${type.name}"$disabled>
              <div><strong>$title</strong><small>$description</small>$status</div>
            </label>
            """.trimIndent()
    }

    private fun reasons(
        type: PenaltyType,
        label: String,
    ): String {
        val chips =
            PenaltyReason.entries
                .filter { it.type == type && it.adminSelectable }
                .joinToString("") {
                    val input = """<input type="radio" name="penaltyReason" value="${it.name}" data-reason-type="${type.name}">"""
                    """<label class="chip">$input${escapeHtml(it.label)}</label>"""
                }
        return """<div class="field" data-type="${type.name}"><span>$label</span><div class="chips">$chips</div></div>"""
    }

    /** 예: 유효 2회 · 2시간, 예약 정지 (2027년 02월 08일(월) 15:12까지) */
    private fun short(standing: PenaltyStanding): String {
        val suspendedUntil = standing.suspendedUntil
        if (suspendedUntil != null) return "예약 정지 (${ReservationDateTimeFormatter.formatDateTime(suspendedUntil)}까지)"
        return "유효 ${standing.photoCount}회 · ${PenaltyTextFormatter.hours(requireNotNull(standing.maxDailyBlocks))}"
    }

    private fun rejectionMessage(rejection: PenaltyGrantRejection): String =
        when (rejection) {
            PenaltyGrantRejection.NOT_TARGET -> "페널티 대상 학과의 학생이 아닙니다"
            PenaltyGrantRejection.ALREADY_GRANTED -> "이 예약에 이미 부여된 페널티가 있습니다"
            PenaltyGrantRejection.NOT_IN_EFFECT -> "인증샷 페널티 시행일 전에 이용한 예약입니다"
            PenaltyGrantRejection.DEADLINE_PASSED -> "이용 종료 후 7일이 지나 부여할 수 없습니다"
        }

    private fun grantMessage(outcome: PenaltyActionOutcome): String =
        when (outcome) {
            PenaltyActionOutcome.DONE -> "페널티가 부여되었습니다"
            PenaltyActionOutcome.NOT_FOUND -> "예약을 찾을 수 없습니다"
            PenaltyActionOutcome.INVALID_INPUT -> "페널티 종류와 사유, 처리자를 입력해주세요"
            PenaltyActionOutcome.NOT_TARGET -> "페널티 대상 학과의 학생이 아닙니다"
            PenaltyActionOutcome.ALREADY_GRANTED -> "이 예약에 이미 같은 종류의 페널티가 부여되어 있습니다"
            PenaltyActionOutcome.NOT_IN_EFFECT -> "인증샷 페널티 시행일 전에 이용한 예약입니다"
            PenaltyActionOutcome.DEADLINE_PASSED -> "이용 종료 후 7일이 지난 예약에는 인증샷 미인정 페널티를 부여할 수 없습니다"
            else -> "알 수 없는 오류가 발생했습니다"
        }

    private fun revokeMessage(outcome: PenaltyActionOutcome): String =
        when (outcome) {
            PenaltyActionOutcome.DONE -> "페널티가 취소되었습니다"
            PenaltyActionOutcome.NOT_FOUND -> "페널티를 찾을 수 없습니다"
            PenaltyActionOutcome.INVALID_INPUT -> "취소 사유와 처리자를 입력해주세요"
            PenaltyActionOutcome.ALREADY_REVOKED -> "이미 취소된 페널티입니다"
            PenaltyActionOutcome.EXPIRED -> "이미 만료된 페널티입니다"
            else -> "알 수 없는 오류가 발생했습니다"
        }
}
