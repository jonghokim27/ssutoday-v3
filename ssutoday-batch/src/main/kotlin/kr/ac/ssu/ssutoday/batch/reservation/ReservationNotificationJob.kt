package kr.ac.ssu.ssutoday.batch.reservation

import io.github.oshai.kotlinlogging.KotlinLogging
import kr.ac.ssu.ssutoday.application.reservation.ReservationCommandApplicationService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ReservationNotificationJob(
    private val reservationCommandApplicationService: ReservationCommandApplicationService,
) {
    private val log = KotlinLogging.logger {}

    // 매 시 :25, :55 — 이용 시작/종료 5분 전 알림
    @Scheduled(cron = "0 25,55 * * * *")
    fun sendUsageSoonNotification() {
        runCatching { reservationCommandApplicationService.sendStartSoonNotifications() }
            .onFailure { log.error(it) { "예약 시작 알림 전송 실패" } }
        runCatching { reservationCommandApplicationService.sendEndSoonNotifications() }
            .onFailure { log.error(it) { "예약 종료 알림 전송 실패" } }
    }

    // 매 시 :00:10, :30:10 — 예약 블록 시작 시 인증샷 촬영 알림
    @Scheduled(cron = "10 0,30 * * * *")
    fun sendVerifyPhotoNotification() {
        runCatching { reservationCommandApplicationService.sendVerifyPhotoNotifications() }
            .onFailure { log.error(it) { "인증샷 촬영 알림 전송 실패" } }
    }
}
