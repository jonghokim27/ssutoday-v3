package kr.ac.ssu.ssutoday.adapter.push

import kr.ac.ssu.ssutoday.core.port.PushTopicManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.stereotype.Component

@Component
@ConditionalOnMissingBean(PushTopicManager::class)
class MockPushTopicManager : PushTopicManager {
    override fun subscribe(token: String, topics: List<String>) {}
    override fun unsubscribe(token: String, topics: List<String>) {}
}
