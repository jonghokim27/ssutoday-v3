package kr.ac.ssu.ssutoday.application.penalty.dto

import kr.ac.ssu.ssutoday.domain.penalty.PenaltyRelief
import java.time.LocalDateTime

data class PenaltyStatusResult(
    /** 페널티 대상 학과의 학생인지. 대상이 아니면 앱에 페널티 버튼을 보여주지 않는다. */
    val applicable: Boolean,
    val photoCount: Int,
    /** 하루 최대 예약 블록 수. 예약 정지 중이면 null이다. */
    val maxDailyBlocks: Int?,
    val suspendedUntil: LocalDateTime?,
    val fraud: Boolean,
    val nextRelief: PenaltyRelief?,
    /** 유효한 페널티를 최근 부여 순으로 담는다. */
    val penalties: List<PenaltyItem>,
)
