package kr.ac.ssu.ssutoday.domain.reservation

import org.springframework.data.jpa.repository.JpaRepository

interface ReservationRequestSignalRepository : JpaRepository<ReservationRequestSignal, Long>
