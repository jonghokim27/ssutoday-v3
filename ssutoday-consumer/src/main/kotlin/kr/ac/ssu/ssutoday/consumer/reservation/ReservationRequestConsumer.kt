package kr.ac.ssu.ssutoday.consumer.reservation

import kr.ac.ssu.ssutoday.application.reservation.ReservationCommandApplicationService
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class ReservationRequestConsumer(
    private val reservationCommandApplicationService: ReservationCommandApplicationService,
) {
    @KafkaListener(
        topics = ["\${ssutoday.kafka.topics.reservation-request:requestReserve}"],
        groupId = "\${spring.kafka.consumer.reservation-group-id:ssutoday-reservation}",
    )
    fun consume(requestId: String) {
        reservationCommandApplicationService.processReservationRequest(requestId.toLong())
    }
}
