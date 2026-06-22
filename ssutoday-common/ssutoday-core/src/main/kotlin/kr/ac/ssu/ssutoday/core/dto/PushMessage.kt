package kr.ac.ssu.ssutoday.core.dto

data class PushMessage(
    val type: String,
    val topic: String? = null,
    val token: String? = null,
    val title: String,
    val body: String,
    val link: String,
)
