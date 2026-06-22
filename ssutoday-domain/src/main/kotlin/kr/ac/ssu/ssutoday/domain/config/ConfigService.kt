package kr.ac.ssu.ssutoday.domain.config

import org.springframework.stereotype.Service

@Service
class ConfigService(
    private val configRepository: ConfigRepository,
) {
    fun isReservationRequestDisabled(): Boolean =
        configRepository.findById(RESERVATION_REQUEST_DISABLED)
            .map { it.value == "true" }
            .orElse(true)

    private companion object {
        const val RESERVATION_REQUEST_DISABLED = "RESERVE_REQUEST_DISABLED"
    }
}
