package kr.ac.ssu.ssutoday.core.penalty

/** 페널티 종류. 인증샷 페널티는 누적 횟수로 예약 시간을 줄이고, 부정 예약은 횟수와 별개로 예약을 정지한다. */
enum class PenaltyType(
    val label: String,
) {
    PHOTO("인증샷 미촬영·미인정"),
    FRAUD("부정 예약"),
}
