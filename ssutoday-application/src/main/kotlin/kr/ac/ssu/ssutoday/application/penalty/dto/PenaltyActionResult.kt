package kr.ac.ssu.ssutoday.application.penalty.dto

/** details는 결과 화면에 그대로 보여줄 항목 이름과 값이다. 처리하지 못했으면 비어 있다. */
data class PenaltyActionResult(
    val outcome: PenaltyActionOutcome,
    val details: List<Pair<String, String>> = emptyList(),
)
