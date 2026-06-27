package kr.ac.ssu.ssutoday.batch.reservation

import io.github.oshai.kotlinlogging.KotlinLogging
import kr.ac.ssu.ssutoday.application.reservation.ReservationCommandApplicationService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ReservationPhotoCheckJob(
    private val reservationCommandApplicationService: ReservationCommandApplicationService,
) {
    private val log = KotlinLogging.logger {}

    @Scheduled(cron = "0 * * * * *")
    fun execute() {
        runCatching { reservationCommandApplicationService.cancelMissingPhotos() }
            .onFailure { log.error(it) { "인증샷 미촬영 예약 취소 처리 실패" } }
    }
}
