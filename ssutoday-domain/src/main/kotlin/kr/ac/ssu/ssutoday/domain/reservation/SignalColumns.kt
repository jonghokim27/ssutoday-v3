package kr.ac.ssu.ssutoday.domain.reservation

import kr.ac.ssu.ssutoday.core.attestation.PlayIntegritySignals
import kr.ac.ssu.ssutoday.core.attestation.RiskFlag

/** MySQL FIND_IN_SET으로 조회할 수 있도록 선언 순서대로 공백 없이 쉼표로 잇는다. 신호가 없으면 빈 문자열이다. */
internal fun Set<RiskFlag>.toFlagsColumn(): String = sorted().joinToString(",")

internal fun PlayIntegritySignals?.toAppsDetectedColumn(): String? = this?.appsDetected?.joinToString(",")?.takeIf(String::isNotEmpty)
