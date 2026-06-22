package kr.ac.ssu.ssutoday.adapter.kafka

import kr.ac.ssu.ssutoday.core.port.ReservationRequestPublisher
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class KafkaReservationRequestPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    @Value("\${ssutoday.kafka.topics.reservation-request:requestReserve}")
    private val topic: String,
) : ReservationRequestPublisher {
    override fun publish(requestId: Long) {
        kafkaTemplate.send(topic, requestId.toString()).get(3, TimeUnit.SECONDS)
    }
}
