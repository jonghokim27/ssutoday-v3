package kr.ac.ssu.ssutoday.core.attestation

/**
 * Google 판정의 선택 응답. Play Console에서 켜지 않은 항목은 비어 있다.
 * VERIFIED 판정에는 쓰지 않고 검토 신호로만 기록한다.
 */
data class PlayIntegritySignals(
    val appsDetected: List<String> = emptyList(),
    val playProtectVerdict: String? = null,
    val deviceActivityLevel: String? = null,
    val strongIntegrity: Boolean = false,
    val appLicensingVerdict: String? = null,
)
