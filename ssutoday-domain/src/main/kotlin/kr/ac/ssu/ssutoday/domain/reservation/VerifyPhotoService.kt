package kr.ac.ssu.ssutoday.domain.reservation

import kr.ac.ssu.ssutoday.core.exception.BusinessException
import kr.ac.ssu.ssutoday.core.status.StatusCode
import kr.ac.ssu.ssutoday.domain.reservation.factory.toView
import org.springframework.stereotype.Service

@Service
class VerifyPhotoService(
    private val repository: VerifyPhotoRepository,
) {
    fun create(reservationId: Long, url: String) {
        if (repository.findByReservationId(reservationId) != null) {
            throw BusinessException(StatusCode.SSU4200)
        }
        repository.save(VerifyPhoto(reservationId = reservationId, url = url))
    }

    fun find(reservationId: Long): VerifyPhotoView? =
        repository.findByReservationId(reservationId)?.toView()

    fun delete(reservationId: Long): Boolean {
        val photo = repository.findByReservationId(reservationId) ?: return false
        repository.delete(photo)
        return true
    }

    fun createException(reservationId: Long): Boolean {
        if (repository.findByReservationId(reservationId) != null) return false
        repository.save(VerifyPhoto(reservationId = reservationId, url = EXCEPTION_PHOTO_URL))
        return true
    }

    private companion object {
        const val EXCEPTION_PHOTO_URL = "https://r2.ssu.today/verifyPhoto/except.png"
    }
}
