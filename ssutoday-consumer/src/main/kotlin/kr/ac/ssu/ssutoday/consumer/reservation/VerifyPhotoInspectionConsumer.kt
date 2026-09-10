package kr.ac.ssu.ssutoday.consumer.reservation

import io.github.oshai.kotlinlogging.KotlinLogging
import kr.ac.ssu.ssutoday.application.reservation.VerifyPhotoApplicationService
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class VerifyPhotoInspectionConsumer(
    private val verifyPhotoApplicationService: VerifyPhotoApplicationService,
) {
    private val log = KotlinLogging.logger {}

    @KafkaListener(
        topics = ["\${ssutoday.kafka.topics.verify-photo-inspection:verifyPhotoInspection}"],
        groupId = "\${spring.kafka.consumer.group-id:ssutoday}",
    )
    fun consume(reservationId: String) {
        log.info { "Verify photo inspection received: $reservationId" }
        verifyPhotoApplicationService.inspect(reservationId.toLong())
    }
}
