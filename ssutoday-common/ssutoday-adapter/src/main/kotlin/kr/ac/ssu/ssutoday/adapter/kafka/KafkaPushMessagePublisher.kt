package kr.ac.ssu.ssutoday.adapter.kafka

import kr.ac.ssu.ssutoday.core.dto.PushMessage
import kr.ac.ssu.ssutoday.core.port.PushMessagePublisher
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.TimeUnit

@Component
class KafkaPushMessagePublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper,
    @Value("\${ssutoday.kafka.topics.push-message:pushMessage}")
    private val topic: String,
) : PushMessagePublisher {
    override fun publish(message: PushMessage) {
        kafkaTemplate.send(topic, objectMapper.writeValueAsString(message)).get(3, TimeUnit.SECONDS)
    }
}
