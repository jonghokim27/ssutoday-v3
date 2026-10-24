package kr.ac.ssu.ssutoday.application.penalty.dto

import kr.ac.ssu.ssutoday.core.penalty.PenaltyReason

data class GrantPenaltyCommand(
    /** 예약의 관리자 토큰. */
    val adminToken: String,
    val reason: PenaltyReason?,
    val adminName: String?,
    /** 부정 예약일 때 아직 이용을 시작하지 않은 다른 예약도 함께 취소할지. */
    val cancelUpcoming: Boolean,
)
