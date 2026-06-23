package kr.ac.ssu.ssutoday.consumer.push

import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.AndroidNotification
import com.google.firebase.messaging.ApnsConfig
import com.google.firebase.messaging.Aps
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import io.github.oshai.kotlinlogging.KotlinLogging
import kr.ac.ssu.ssutoday.core.dto.PushMessage
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class PushMessageConsumer(
    private val firebaseMessaging: FirebaseMessaging,
    private val objectMapper: ObjectMapper,
) {
    private val log = KotlinLogging.logger {}

    @KafkaListener(
        topics = ["\${ssutoday.kafka.topics.push-message:pushMessage}"],
        groupId = "\${spring.kafka.consumer.group-id:ssutoday-push}",
    )
    fun consume(payload: String) {
        val push = objectMapper.readValue(payload, PushMessage::class.java)
        val builder =
            Message
                .builder()
                .setNotification(
                    Notification
                        .builder()
                        .setTitle(push.title)
                        .setBody(push.body)
                        .build(),
                ).putData("link", push.link)
                .setAndroidConfig(
                    AndroidConfig
                        .builder()
                        .setNotification(AndroidNotification.builder().setPriority(AndroidNotification.Priority.HIGH).build())
                        .build(),
                ).setApnsConfig(
                    ApnsConfig
                        .builder()
                        .putHeader("apns-priority", "10")
                        .setAps(Aps.builder().setContentAvailable(true).build())
                        .build(),
                )
        when (push.type) {
            "topic" -> builder.setTopic(requireNotNull(push.topic))
            "token" -> builder.setToken(requireNotNull(push.token))
            else -> error("Unsupported push type: ${push.type}")
        }
        log.info { "Firebase message sent: ${firebaseMessaging.send(builder.build())}" }
    }
}
