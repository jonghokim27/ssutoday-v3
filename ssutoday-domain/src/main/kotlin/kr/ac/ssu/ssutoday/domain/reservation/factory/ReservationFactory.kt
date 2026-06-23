package kr.ac.ssu.ssutoday.domain.reservation.factory

import kr.ac.ssu.ssutoday.domain.reservation.Reservation
import kr.ac.ssu.ssutoday.domain.reservation.ReservationView

fun Reservation.toView() =
    ReservationView(
        id = id,
        studentId = studentId,
        roomNo = roomNo,
        date = date,
        startBlock = startBlock,
        endBlock = endBlock,
        createdAt = createdAt,
        deletedAt = deletedAt,
        deletedReason = deletedReason,
        active = active,
    )
