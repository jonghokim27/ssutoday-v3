package kr.ac.ssu.ssutoday.domain.reservation.factory

import kr.ac.ssu.ssutoday.domain.reservation.ReservationRequest
import kr.ac.ssu.ssutoday.domain.reservation.ReservationRequestView

fun ReservationRequest.toView() = ReservationRequestView(
    id = id,
    studentId = studentId,
    roomNo = roomNo,
    date = date,
    startBlock = startBlock,
    endBlock = endBlock,
    status = status,
)
