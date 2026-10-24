package kr.ac.ssu.ssutoday.domain.penalty

/** 예약에 페널티를 부여할 수 없는 이유. */
enum class PenaltyGrantRejection {
    /** 페널티 대상 학과의 학생이 아니다. */
    NOT_TARGET,

    /** 같은 예약에 같은 종류의 페널티가 이미 있다. */
    ALREADY_GRANTED,

    /** 인증샷 페널티 시행일 전에 이용한 예약이다. */
    NOT_IN_EFFECT,
}
