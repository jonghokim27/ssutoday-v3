package kr.ac.ssu.ssutoday.api.room.mapper

import kr.ac.ssu.ssutoday.api.room.dto.RoomView
import kr.ac.ssu.ssutoday.application.reservation.dto.RoomReservation
import kr.ac.ssu.ssutoday.domain.room.RoomView as DomainRoomView

fun DomainRoomView.toView(reservations: List<RoomReservation>) = RoomView(no, name, capacity, location, tags, image, bigImage, reservations)
