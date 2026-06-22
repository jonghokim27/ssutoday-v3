package kr.ac.ssu.ssutoday.api.room.dto

import jakarta.validation.constraints.Pattern

data class RoomListRequest(
    @field:Pattern(regexp = "202[3-9]-(0[1-9]|1[0-2])-(0[1-9]|1[0-9]|2[0-9]|3[0-1])")
    val date: String,
)
