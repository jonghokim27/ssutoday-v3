package kr.ac.ssu.ssutoday.core.attestation

/**
 * 관리자 검토용 신호. 요청을 거부하는 데 쓰지 않으며 한 요청에 여러 개가 함께 붙을 수 있다.
 * 저장과 알림은 선언 순서를 따른다.
 */
enum class RiskFlag {
    OPEN_RUSH,
    ACCESS_RISK,
    PLAY_PROTECT_RISK,
    DEVICE_ACTIVITY_HIGH,
    UNLICENSED,
}
