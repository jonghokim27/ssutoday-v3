package kr.ac.ssu.ssutoday.core.penalty

/**
 * 페널티 부여 사유. 이름은 DB와 API에 그대로 저장·노출되므로 바꾸지 않는다.
 * label은 관리자 화면, Discord, 푸시 알림에 쓰는 짧은 문구다.
 */
enum class PenaltyReason(
    val type: PenaltyType,
    val label: String,
    val adminSelectable: Boolean,
) {
    PHOTO_MISSING(PenaltyType.PHOTO, "인증샷 미촬영 자동 취소", false),
    PHOTO_REJECTED(PenaltyType.PHOTO, "인증샷 2회 미인정 취소", false),
    PHOTO_OUTSIDE(PenaltyType.PHOTO, "스터디룸 외부에서 촬영된 인증샷", true),
    FRAUD_AUTOMATION(PenaltyType.FRAUD, "매크로 등 자동화 프로그램 이용", true),
    FRAUD_ACCOUNT(PenaltyType.FRAUD, "타인 계정 이용 또는 계정 대여", true),
    FRAUD_PHOTO(PenaltyType.FRAUD, "인증샷 재사용 또는 조작", true),
}
