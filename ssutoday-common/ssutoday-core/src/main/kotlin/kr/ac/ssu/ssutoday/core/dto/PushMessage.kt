package kr.ac.ssu.ssutoday.core.dto

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class PushMessage
    @JsonCreator
    constructor(
        @JsonProperty("type") val type: String,
        @JsonProperty("topic") val topic: String? = null,
        @JsonProperty("token") val token: String? = null,
        @JsonProperty("title") val title: String,
        @JsonProperty("body") val body: String,
        @JsonProperty("category") val category: String,
        @JsonProperty("url") val url: String? = null,
        // 앱이 알림을 눌렀을 때 웹뷰에서 열 화면 경로. 예: /reservations/penalties
        @JsonProperty("path") val path: String? = null,
    )
