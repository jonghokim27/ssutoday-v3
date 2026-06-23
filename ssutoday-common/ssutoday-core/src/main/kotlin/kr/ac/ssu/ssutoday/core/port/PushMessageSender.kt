package kr.ac.ssu.ssutoday.core.port

import kr.ac.ssu.ssutoday.core.dto.PushMessage

interface PushMessageSender {
    fun send(message: PushMessage): String
}
