package kr.ac.ssu.ssutoday.domain.config

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class ConfigService(
    private val configRepository: ConfigRepository,
) {
    fun isReservationRequestDisabled(): Boolean = configRepository.findByIdOrNull(RESERVATION_REQUEST_DISABLED)?.value == "true"

    private companion object {
        const val RESERVATION_REQUEST_DISABLED = "RESERVE_REQUEST_DISABLED"
    }
}
