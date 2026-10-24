package kr.ac.ssu.ssutoday.application.penalty.dto

import kr.ac.ssu.ssutoday.domain.penalty.PenaltyStanding

data class PenaltyRevokeForm(
    /** 대상, 페널티 종류, 부여 사유처럼 화면에 그대로 보여줄 항목 이름과 값이다. */
    val details: List<Pair<String, String>>,
    val current: PenaltyStanding,
    val after: PenaltyStanding,
    /** 이미 취소했거나 만료돼 취소할 수 없으면 그 결과. */
    val unavailable: PenaltyActionOutcome?,
)
