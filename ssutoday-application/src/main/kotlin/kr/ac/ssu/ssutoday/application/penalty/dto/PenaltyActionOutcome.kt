package kr.ac.ssu.ssutoday.application.penalty.dto

/** 관리자 페널티 부여·취소 처리 결과. */
enum class PenaltyActionOutcome {
    DONE,
    NOT_FOUND,
    INVALID_INPUT,
    ALREADY_GRANTED,
    NOT_IN_EFFECT,
    DEADLINE_PASSED,
    ALREADY_REVOKED,
    EXPIRED,
}
