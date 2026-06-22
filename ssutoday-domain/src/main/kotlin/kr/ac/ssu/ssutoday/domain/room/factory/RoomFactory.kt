package kr.ac.ssu.ssutoday.domain.room.factory

import kr.ac.ssu.ssutoday.domain.room.Room
import kr.ac.ssu.ssutoday.domain.room.RoomView

fun Room.toView() = RoomView(
    no = no,
    name = name,
    major = major,
    capacity = capacity,
    location = location,
    tags = tags,
    image = image,
    bigImage = bigImage,
    availableValue = availableValue,
    isAvailable = isAvailable,
)
