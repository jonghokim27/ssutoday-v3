package kr.ac.ssu.ssutoday.domain.reservation

import org.springframework.data.jpa.repository.JpaRepository

interface ReservationRequestRepository : JpaRepository<ReservationRequest, Long> {
    fun findByIdAndStudentId(id: Long, studentId: Int): ReservationRequest?
}
