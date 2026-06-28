package kr.ac.ssu.ssutoday.domain.reservation

import kr.ac.ssu.ssutoday.core.exception.BusinessException
import kr.ac.ssu.ssutoday.core.status.StatusCode
import kr.ac.ssu.ssutoday.domain.reservation.factory.toView
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class VerifyPhotoService(
    private val repository: VerifyPhotoRepository,
    @Value("\${ssutoday.storage.public-base-url:}")
    private val publicBaseUrl: String,
) {
    fun create(
        reservationId: Long,
        url: String,
    ) {
        if (repository.findByReservationId(reservationId) != null) {
            throw BusinessException(StatusCode.SSU4200)
        }
        repository.save(VerifyPhoto(reservationId = reservationId, url = url))
    }

    fun find(reservationId: Long): VerifyPhotoView? = repository.findByReservationId(reservationId)?.toView()

    fun delete(reservationId: Long): Boolean {
        val photo = repository.findByReservationId(reservationId) ?: return false
        repository.delete(photo)
        return true
    }

    fun createException(reservationId: Long): Boolean {
        if (repository.findByReservationId(reservationId) != null) return false
        repository.save(VerifyPhoto(reservationId = reservationId, url = buildPublicUrl(EXCEPTION_PHOTO_KEY)))
        return true
    }

    private fun buildPublicUrl(key: String): String = "${publicBaseUrl.trimEnd('/')}/$key"

    private companion object {
        const val EXCEPTION_PHOTO_KEY = "verifyPhoto/except.png"
    }
}
