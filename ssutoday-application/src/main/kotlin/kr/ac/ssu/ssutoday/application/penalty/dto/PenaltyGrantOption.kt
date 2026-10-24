package kr.ac.ssu.ssutoday.application.penalty.dto

import kr.ac.ssu.ssutoday.domain.penalty.PenaltyGrantRejection
import kr.ac.ssu.ssutoday.domain.penalty.PenaltyStanding

data class PenaltyGrantOption(
    /** 부여할 수 없으면 그 이유. */
    val rejection: PenaltyGrantRejection?,
    /** 지금 부여했을 때의 상태. */
    val after: PenaltyStanding,
)
