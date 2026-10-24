package kr.ac.ssu.ssutoday.core.port

interface DiscordPenaltyNotificationPort {
    /**
     * fields는 embed 필드의 이름과 값이며 순서대로 표시한다.
     * revokeToken이 있으면 관리자 작업 페이지로 가는 페널티 취소 버튼을 붙인다.
     */
    fun send(
        content: String,
        fields: List<Pair<String, String>>,
        revokeToken: String? = null,
    )
}
