package kr.ac.ssu.ssutoday.domain.reservation

import kr.ac.ssu.ssutoday.core.attestation.PlayIntegritySignals
import kr.ac.ssu.ssutoday.core.attestation.RiskFlag
import org.springframework.stereotype.Component

/** 요청을 거부하지 않고 검토 대상만 표시한다. 조건을 만족하는 신호를 모두 붙인다. */
@Component
class ReservationRiskPolicy {
    /** openOffsetMillis는 예약 오픈 시각 대비 접수 시각이다. 인증샷처럼 오픈과 무관하면 null을 넘긴다. */
    fun flags(
        openOffsetMillis: Long?,
        signals: PlayIntegritySignals?,
    ): Set<RiskFlag> {
        val flags = sortedSetOf<RiskFlag>()
        if (openOffsetMillis != null && openOffsetMillis in 0 until OPEN_RUSH_MILLIS) flags += RiskFlag.OPEN_RUSH
        if (signals == null) return flags
        if (signals.appsDetected.any { it in CONTROLLING_APPS }) flags += RiskFlag.ACCESS_RISK
        if (signals.playProtectVerdict in RISKY_PLAY_PROTECT) flags += RiskFlag.PLAY_PROTECT_RISK
        if (signals.deviceActivityLevel in HIGH_DEVICE_ACTIVITY) flags += RiskFlag.DEVICE_ACTIVITY_HIGH
        if (signals.appLicensingVerdict == UNLICENSED) flags += RiskFlag.UNLICENSED
        return flags
    }

    private companion object {
        const val OPEN_RUSH_MILLIS = 1_000L
        const val UNLICENSED = "UNLICENSED"
        val CONTROLLING_APPS = setOf("KNOWN_CONTROLLING", "UNKNOWN_CONTROLLING")
        val RISKY_PLAY_PROTECT = setOf("MEDIUM_RISK", "HIGH_RISK")
        val HIGH_DEVICE_ACTIVITY = setOf("LEVEL_3", "LEVEL_4")
    }
}
