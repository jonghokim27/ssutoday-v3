package kr.ac.ssu.ssutoday.core.port

interface VerifyPhotoInspectionPublisher {
    fun publish(reservationId: Long)
}
