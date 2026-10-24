package kr.ac.ssu.ssutoday.api.reservation.dto

data class AdminPenaltyResponse(
    /** API 서버 기준 페널티 부여 화면 경로. 앱은 API 주소에 붙여 앱 안 브라우저로 연다. */
    val path: String,
)
