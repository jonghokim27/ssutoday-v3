package kr.ac.ssu.ssutoday.adapter.push

import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.AndroidNotification
import com.google.firebase.messaging.ApnsConfig
import com.google.firebase.messaging.Aps
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import kr.ac.ssu.ssutoday.core.dto.PushMessage
import kr.ac.ssu.ssutoday.core.port.PushMessageSender
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("live")
class FirebasePushSender(
    private val firebaseMessaging: FirebaseMessaging,
) : PushMessageSender {
    override fun send(message: PushMessage): String {
        val builder =
            Message
                .builder()
                .setNotification(
                    Notification
                        .builder()
                        .setTitle(message.title)
                        .setBody(message.body)
                        .build(),
                ).putData("link", message.link)
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

        when (message.type) {
            "topic" -> builder.setTopic(requireNotNull(message.topic))
            "token" -> builder.setToken(requireNotNull(message.token))
            else -> error("Unsupported push type: ${message.type}")
        }

        return firebaseMessaging.send(builder.build())
    }
}
