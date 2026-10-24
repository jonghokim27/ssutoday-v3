package kr.ac.ssu.ssutoday.application.penalty.dto

import kr.ac.ssu.ssutoday.domain.penalty.PenaltyStanding

data class PenaltyGrantForm(
    /** 예약자, 시설명, 이용 일시처럼 화면에 그대로 보여줄 항목 이름과 값이다. */
    val details: List<Pair<String, String>>,
    val photoUrl: String?,
    val current: PenaltyStanding,
    val photo: PenaltyGrantOption,
    val fraud: PenaltyGrantOption,
    /** 부정 예약 정지 시 함께 취소할 수 있는 다른 예약. */
    val upcomingReservations: List<String>,
)
