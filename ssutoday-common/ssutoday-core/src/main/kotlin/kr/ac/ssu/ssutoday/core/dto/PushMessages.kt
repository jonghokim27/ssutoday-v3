package kr.ac.ssu.ssutoday.core.dto

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object PushMessages {
    // ── 예약 확정 ─────────────────────────────────────────────────────────────
    fun reserveConfirmTitle(roomName: String) = "✅ $roomName 예약이 확정되었어요"

    fun reserveConfirmBody(
        date: LocalDate,
        startBlock: Int,
        endBlock: Int,
    ): String {
        val dow = koreanDayOfWeek(date.dayOfWeek)
        val startTime = LocalTime.MIDNIGHT.plusMinutes(startBlock * 30L).format(HM)
        val endTime = LocalTime.MIDNIGHT.plusMinutes((endBlock + 1) * 30L).format(HM)
        return "${date.year}년 ${date.monthValue}월 ${date.dayOfMonth}일($dow) $startTime ~ $endTime"
    }

    // ── 인증샷 촬영 요청 ──────────────────────────────────────────────────────
    const val VERIFY_PHOTO_TITLE = "📷 10분 내로 인증샷을 촬영해주세요"
    const val VERIFY_PHOTO_BODY = "이 알림을 터치하여 인증샷을 촬영해주세요. 인증샷이 촬영되지 않으면 예약이 취소돼요"

    // ── 관리자 예약 취소 ──────────────────────────────────────────────────────
    fun adminCancelTitle(roomName: String) = "❌ $roomName 예약이 취소되었어요"

    fun adminCancelBody(reason: String) = "관리자가 예약을 취소했어요. (사유: $reason)"

    // ── 관리자 인증샷 삭제 (재촬영 요청) ──────────────────────────────────────
    const val PHOTO_DELETE_TITLE = "❗ 5분 내로 인증샷을 다시 촬영해주세요"
    const val PHOTO_DELETE_BODY =
        "촬영해주신 인증샷으로 스터디룸에 입실하였음을 확인할 수 없었어요. 이 알림을 터치하여 인증샷을 다시 촬영해주세요. 인증샷이 다시 촬영되지 않으면 예약이 취소돼요"

    // ── 관리자 인증샷 예외 처리 ───────────────────────────────────────────────
    const val PHOTO_EXCEPT_TITLE = "🛠️ 인증샷 촬영 예외로 처리되었어요"
    const val PHOTO_EXCEPT_BODY = "인증샷을 촬영하지 않아도 예약이 취소되지 않아요. 인증샷 촬영 예외는 해당 예약에만 적용돼요"

    // ── 이용 곧 시작 ──────────────────────────────────────────────────────────
    fun reserveStartSoonTitle(roomName: String) = "🛎️ $roomName 이용이 곧 시작돼요"

    const val RESERVE_START_SOON_BODY = "5분 뒤 이용이 시작돼요. 이용이 불가능하다면, 다른 이용자를 위해 예약을 취소해주세요"

    // ── 이용 곧 종료 ──────────────────────────────────────────────────────────
    fun reserveEndSoonTitle(roomName: String) = "🚨 $roomName 이용이 곧 종료돼요"

    const val RESERVE_END_SOON_BODY = "5분 뒤 이용이 종료돼요. 다음 이용자를 위해 자리를 정리하고 퇴실해주세요"

    // ── 인증샷 미촬영 자동 취소 ───────────────────────────────────────────────
    fun autoCancelTitle(roomName: String) = "❌ $roomName 예약이 취소되었어요"

    const val AUTO_CANCEL_BODY = "인증샷을 촬영하지 않아 예약이 취소되었어요"

    const val AUTO_CANCEL_PENALTY_BODY = "인증샷을 촬영하지 않아 예약이 취소되고 페널티 1회가 부여되었어요"

    // ── 인증샷 2회 미인정 취소 ────────────────────────────────────────────────
    const val INSPECTION_CANCEL_PENALTY_BODY = "인증샷이 2회 인정되지 않아 예약이 취소되고 페널티 1회가 부여되었어요"

    // ── 관리자 페널티 부여 ────────────────────────────────────────────────────
    const val PENALTY_GRANT_TITLE = "⚠️ 페널티 1회가 부여되었어요"

    fun penaltyGrantBody(
        reservationDate: LocalDate,
        roomName: String,
    ) = "${monthDay(reservationDate)} $roomName 이용 건의 인증샷이 스터디룸 외부에서 촬영된 것으로 확인되었어요"

    // ── 부정 예약 정지 ────────────────────────────────────────────────────────
    const val SUSPENSION_TITLE = "⛔ 스터디룸 예약이 정지되었어요"

    fun suspensionBody(
        reservationCancelled: Boolean,
        suspendedUntil: LocalDate,
        reason: String,
    ): String {
        val head = if (reservationCancelled) "부정 예약이 확인되어 해당 예약이 취소되었어요." else "부정 예약이 확인되었어요."
        return "$head ${suspendedUntil.year}년 ${monthDay(suspendedUntil)}까지 신규 예약이 제한돼요. (사유: $reason)"
    }

    // ── 페널티 취소 ───────────────────────────────────────────────────────────
    const val PENALTY_REVOKE_TITLE = "✅ 페널티 1회가 취소되었어요"

    fun penaltyRevokeBody(grantedAt: LocalDate) = "${monthDay(grantedAt)}에 부여된 페널티가 취소되었어요"

    fun fraudRevokeBody(grantedAt: LocalDate) = "${monthDay(grantedAt)}에 부여된 부정 예약 페널티가 취소되었어요"

    const val SUSPENSION_LIFT_TITLE = "🔓 예약 정지가 해제되었어요"

    fun suspensionLiftBody(grantedAt: LocalDate) = "${monthDay(grantedAt)}에 부여된 부정 예약 페널티가 취소되어 지금부터 예약할 수 있어요"

    // ──────────────────────────────────────────────────────────────────────────

    private fun monthDay(date: LocalDate) = "${date.monthValue}월 ${date.dayOfMonth}일(${koreanDayOfWeek(date.dayOfWeek)})"

    private val HM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    private fun koreanDayOfWeek(dayOfWeek: DayOfWeek) =
        when (dayOfWeek) {
            DayOfWeek.MONDAY -> "월"
            DayOfWeek.TUESDAY -> "화"
            DayOfWeek.WEDNESDAY -> "수"
            DayOfWeek.THURSDAY -> "목"
            DayOfWeek.FRIDAY -> "금"
            DayOfWeek.SATURDAY -> "토"
            DayOfWeek.SUNDAY -> "일"
        }
}
