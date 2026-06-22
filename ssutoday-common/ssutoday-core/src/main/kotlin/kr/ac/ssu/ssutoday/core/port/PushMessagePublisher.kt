package kr.ac.ssu.ssutoday.core.port

import kr.ac.ssu.ssutoday.core.dto.PushMessage

interface PushMessagePublisher {
    fun publish(message: PushMessage)
}
