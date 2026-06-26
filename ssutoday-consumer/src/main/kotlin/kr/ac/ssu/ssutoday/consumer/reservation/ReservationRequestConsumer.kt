package kr.ac.ssu.ssutoday.consumer.reservation

import io.github.oshai.kotlinlogging.KotlinLogging
import kr.ac.ssu.ssutoday.application.reservation.ReservationCommandApplicationService
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class ReservationRequestConsumer(
    private val reservationCommandApplicationService: ReservationCommandApplicationService,
) {
    private val log = KotlinLogging.logger {}

    @KafkaListener(
        topics = ["\${ssutoday.kafka.topics.reservation-request:requestReserve}"],
        groupId = "\${spring.kafka.consumer.group-id:ssutoday}",
    )
    fun consume(requestId: String) {
        log.info { "Reservation request received: $requestId" }
        reservationCommandApplicationService.processReservationRequest(requestId.toLong())
    }
}
