package kr.ac.ssu.ssutoday.application.push

import kr.ac.ssu.ssutoday.core.dto.PushMessage
import kr.ac.ssu.ssutoday.core.port.PushMessageSender
import org.springframework.stereotype.Service

@Service
class PushMessageApplicationService(
    private val pushMessageSender: PushMessageSender,
) {
    fun send(message: PushMessage): String = pushMessageSender.send(message)
}
