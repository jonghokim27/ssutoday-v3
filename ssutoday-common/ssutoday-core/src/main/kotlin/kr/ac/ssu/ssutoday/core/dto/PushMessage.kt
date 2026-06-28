package kr.ac.ssu.ssutoday.core.dto

import tools.jackson.annotation.JsonCreator
import tools.jackson.annotation.JsonProperty

data class PushMessage
@JsonCreator
constructor(
    @JsonProperty("type") val type: String,
    @JsonProperty("topic") val topic: String? = null,
    @JsonProperty("token") val token: String? = null,
    @JsonProperty("title") val title: String,
    @JsonProperty("body") val body: String,
    @JsonProperty("link") val link: String,
)
