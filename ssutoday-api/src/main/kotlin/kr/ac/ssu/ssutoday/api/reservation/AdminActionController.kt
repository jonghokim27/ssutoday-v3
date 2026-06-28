package kr.ac.ssu.ssutoday.api.reservation

import kr.ac.ssu.ssutoday.application.reservation.ReservationCommandApplicationService
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/action")
class AdminActionController(
    private val reservationCommandApplicationService: ReservationCommandApplicationService,
) {
    @GetMapping(produces = [MediaType.TEXT_HTML_VALUE])
    fun executeAction(
        @RequestParam token: String,
        @RequestParam action: String,
    ): ResponseEntity<String> {
        val result = reservationCommandApplicationService.executeAdminActionByToken(token, action)
        val (title, message, success) =
            when (result) {
                0 -> Triple("오류", "예약을 찾을 수 없습니다", false)
                1 -> Triple("오류", "이미 취소된 예약입니다", false)
                2 -> Triple("오류", "종료된 예약입니다", false)
                3 -> Triple("완료", "예약이 취소되었습니다", true)
                4 -> Triple("오류", "인증샷이 존재하지 않습니다", false)
                5 -> Triple("완료", "인증샷이 삭제되었습니다", true)
                else -> Triple("오류", "알 수 없는 오류가 발생했습니다", false)
            }
        return ResponseEntity.ok(buildHtml(title, message, success))
    }

    private fun buildHtml(
        title: String,
        message: String,
        success: Boolean,
    ): String {
        val color = if (success) "#2ecc71" else "#e74c3c"
        val icon = if (success) "✅" else "❌"
        return "<!DOCTYPE html><html lang=\"ko\"><head><meta charset=\"UTF-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
            "<title>$title</title><style>" +
            "body{font-family:-apple-system,sans-serif;display:flex;justify-content:center;" +
            "align-items:center;min-height:100vh;margin:0;background:#f5f5f5;}" +
            ".card{background:white;border-radius:12px;padding:40px;text-align:center;" +
            "box-shadow:0 2px 12px rgba(0,0,0,.1);max-width:360px;width:90%;}" +
            ".icon{font-size:48px;margin-bottom:16px;}" +
            "h1{color:$color;margin:0 0 12px;font-size:22px;}" +
            "p{color:#555;margin:0;font-size:16px;}" +
            "</style></head><body>" +
            "<div class=\"card\"><div class=\"icon\">$icon</div>" +
            "<h1>$title</h1><p>$message</p></div>" +
            "</body></html>"
    }
}
