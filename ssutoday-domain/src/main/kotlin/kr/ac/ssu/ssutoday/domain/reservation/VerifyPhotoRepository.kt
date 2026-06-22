package kr.ac.ssu.ssutoday.domain.reservation

import org.springframework.data.jpa.repository.JpaRepository

interface VerifyPhotoRepository : JpaRepository<VerifyPhoto, Long> {
    fun findByReservationId(reservationId: Long): VerifyPhoto?
}
