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
    const val PHOTO_DELETE_TITLE = "❗ 10분 내로 인증샷을 다시 촬영해주세요"
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

    // ──────────────────────────────────────────────────────────────────────────

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
