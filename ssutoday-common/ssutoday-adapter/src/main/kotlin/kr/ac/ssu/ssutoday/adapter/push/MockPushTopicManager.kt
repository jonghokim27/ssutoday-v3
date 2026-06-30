package kr.ac.ssu.ssutoday.adapter.push

import kr.ac.ssu.ssutoday.core.port.PushTopicManager
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("default")
class MockPushTopicManager : PushTopicManager {
    override fun subscribe(token: String, topics: List<String>) {}
    override fun unsubscribe(token: String, topics: List<String>) {}
}
