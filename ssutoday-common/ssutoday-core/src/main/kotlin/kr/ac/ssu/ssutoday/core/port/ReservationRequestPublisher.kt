package kr.ac.ssu.ssutoday.core.port

interface ReservationRequestPublisher {
    fun publish(requestId: Long)
}
