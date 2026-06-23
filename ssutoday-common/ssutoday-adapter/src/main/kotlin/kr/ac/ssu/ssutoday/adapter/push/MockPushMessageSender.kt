package kr.ac.ssu.ssutoday.adapter.push

import io.github.oshai.kotlinlogging.KotlinLogging
import kr.ac.ssu.ssutoday.core.dto.PushMessage
import kr.ac.ssu.ssutoday.core.port.PushMessageSender
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("default")
class MockPushMessageSender : PushMessageSender {
    private val log = KotlinLogging.logger {}

    override fun send(message: PushMessage): String {
        log.info { "Mock push message sent: type=${message.type}, title=${message.title}" }
        return "mock"
    }
}
