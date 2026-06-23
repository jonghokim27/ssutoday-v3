package kr.ac.ssu.ssutoday.consumer.push

import io.github.oshai.kotlinlogging.KotlinLogging
import kr.ac.ssu.ssutoday.application.push.PushMessageApplicationService
import kr.ac.ssu.ssutoday.core.dto.PushMessage
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class PushMessageConsumer(
    private val pushMessageApplicationService: PushMessageApplicationService,
    private val objectMapper: ObjectMapper,
) {
    private val log = KotlinLogging.logger {}

    @KafkaListener(
        topics = ["\${ssutoday.kafka.topics.push-message:pushMessage}"],
        groupId = "\${spring.kafka.consumer.group-id:ssutoday}",
    )
    fun consume(payload: String) {
        val push = objectMapper.readValue(payload, PushMessage::class.java)
        log.info { "Push message sent: ${pushMessageApplicationService.send(push)}" }
    }
}
