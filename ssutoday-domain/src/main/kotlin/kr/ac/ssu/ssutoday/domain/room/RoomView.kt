package kr.ac.ssu.ssutoday.domain.room

data class RoomView(
    val no: String,
    val name: String,
    val major: String,
    val capacity: Int,
    val location: String,
    val tags: String,
    val image: String,
    val bigImage: String,
    val availableValue: Int,
    val isAvailable: Boolean,
)
