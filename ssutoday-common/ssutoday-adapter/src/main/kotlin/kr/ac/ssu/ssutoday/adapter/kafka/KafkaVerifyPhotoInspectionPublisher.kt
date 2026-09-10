package kr.ac.ssu.ssutoday.adapter.kafka

import kr.ac.ssu.ssutoday.core.port.VerifyPhotoInspectionPublisher
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class KafkaVerifyPhotoInspectionPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    @Value("\${ssutoday.kafka.topics.verify-photo-inspection:verifyPhotoInspection}")
    private val topic: String,
) : VerifyPhotoInspectionPublisher {
    override fun publish(reservationId: Long) {
        kafkaTemplate.send(topic, reservationId.toString()).get(3, TimeUnit.SECONDS)
    }
}
