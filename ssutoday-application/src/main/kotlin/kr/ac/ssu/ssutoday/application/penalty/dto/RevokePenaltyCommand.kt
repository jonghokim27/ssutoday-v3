package kr.ac.ssu.ssutoday.application.penalty.dto

data class RevokePenaltyCommand(
    /** 페널티의 관리자 토큰. */
    val adminToken: String,
    val reason: String?,
    val adminName: String?,
)
