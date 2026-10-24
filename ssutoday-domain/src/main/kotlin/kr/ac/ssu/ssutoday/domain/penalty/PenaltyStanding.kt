package kr.ac.ssu.ssutoday.domain.penalty

import java.time.LocalDateTime

/** 학생의 현재 페널티 상태. 유효한 페널티만으로 계산한다. */
data class PenaltyStanding(
    /** 유효한 인증샷 페널티 횟수. 부정 예약은 세지 않는다. */
    val photoCount: Int,
    /** 하루 최대 예약 블록 수. 예약 정지 중이면 null이다. */
    val maxDailyBlocks: Int?,
    val suspendedUntil: LocalDateTime?,
    /** 부정 예약으로 정지된 상태인지. 누적 정지와 안내를 나눈다. */
    val fraud: Boolean,
    /** 만료로 예약 시간이 처음 늘어나는 시점. 부정 예약 정지 중이거나 늘어날 일이 없으면 null이다. */
    val nextRelief: PenaltyRelief?,
) {
    val suspended: Boolean get() = suspendedUntil != null
}

data class PenaltyRelief(
    val at: LocalDateTime,
    val maxDailyBlocks: Int,
)
