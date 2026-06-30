package kr.ac.ssu.ssutoday.adapter.push

import com.google.firebase.messaging.FirebaseMessaging
import kr.ac.ssu.ssutoday.core.port.PushTopicManager
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("live")
class FirebaseTopicManager(
    private val firebaseMessaging: FirebaseMessaging,
) : PushTopicManager {
    override fun subscribe(token: String, topics: List<String>) {
        topics.forEach { firebaseMessaging.subscribeToTopic(listOf(token), it) }
    }

    override fun unsubscribe(token: String, topics: List<String>) {
        topics.forEach { firebaseMessaging.unsubscribeFromTopic(listOf(token), it) }
    }
}
