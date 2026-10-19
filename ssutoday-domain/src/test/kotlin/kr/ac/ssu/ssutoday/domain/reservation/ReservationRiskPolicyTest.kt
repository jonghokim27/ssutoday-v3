package kr.ac.ssu.ssutoday.domain.reservation

import kr.ac.ssu.ssutoday.core.attestation.PlayIntegritySignals
import kr.ac.ssu.ssutoday.core.attestation.RiskFlag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReservationRiskPolicyTest {
    private val policy = ReservationRiskPolicy()
    private val clean =
        PlayIntegritySignals(
            appsDetected = listOf("KNOWN_INSTALLED", "UNKNOWN_INSTALLED"),
            playProtectVerdict = "NO_ISSUES",
            deviceActivityLevel = "LEVEL_1",
            strongIntegrity = true,
            appLicensingVerdict = "LICENSED",
        )

    @Test
    fun `오픈 후 1초 안에 접수한 요청만 OPEN_RUSH로 표시한다`() {
        mapOf(
            -1L to false,
            0L to true,
            999L to true,
            1_000L to false,
            86_400_000L to false,
        ).forEach { (offset, rush) ->
            assertEquals(rush, RiskFlag.OPEN_RUSH in policy.flags(offset, null), "offset=$offset")
        }
        assertTrue(policy.flags(null, null).isEmpty())
    }

    @Test
    fun `정상 신호는 표시하지 않는다`() {
        assertTrue(policy.flags(5_000, clean).isEmpty())
        assertTrue(policy.flags(5_000, PlayIntegritySignals()).isEmpty())
    }

    @Test
    fun `각 신호는 해당 조건에서만 표시한다`() {
        val cases =
            listOf(
                clean.copy(appsDetected = listOf("UNKNOWN_CONTROLLING")) to RiskFlag.ACCESS_RISK,
                clean.copy(appsDetected = listOf("KNOWN_CONTROLLING")) to RiskFlag.ACCESS_RISK,
                clean.copy(playProtectVerdict = "MEDIUM_RISK") to RiskFlag.PLAY_PROTECT_RISK,
                clean.copy(playProtectVerdict = "HIGH_RISK") to RiskFlag.PLAY_PROTECT_RISK,
                clean.copy(deviceActivityLevel = "LEVEL_3") to RiskFlag.DEVICE_ACTIVITY_HIGH,
                clean.copy(deviceActivityLevel = "LEVEL_4") to RiskFlag.DEVICE_ACTIVITY_HIGH,
                clean.copy(appLicensingVerdict = "UNLICENSED") to RiskFlag.UNLICENSED,
            )
        cases.forEach { (signals, flag) -> assertEquals(setOf(flag), policy.flags(5_000, signals), "$signals") }

        listOf(
            clean.copy(appsDetected = listOf("UNKNOWN_CAPTURING", "UNKNOWN_OVERLAYS")),
            clean.copy(playProtectVerdict = "POSSIBLE_RISK"),
            clean.copy(deviceActivityLevel = "LEVEL_2"),
            clean.copy(deviceActivityLevel = "UNEVALUATED"),
            clean.copy(appLicensingVerdict = "UNEVALUATED"),
        ).forEach { assertTrue(policy.flags(5_000, it).isEmpty(), "$it") }
    }

    @Test
    fun `한 요청에 해당하는 신호를 모두 선언 순서로 붙인다`() {
        val flags =
            policy.flags(
                120,
                PlayIntegritySignals(
                    appsDetected = listOf("UNKNOWN_CONTROLLING"),
                    playProtectVerdict = "HIGH_RISK",
                    deviceActivityLevel = "LEVEL_4",
                    appLicensingVerdict = "UNLICENSED",
                ),
            )
        assertEquals(RiskFlag.entries.toList(), flags.toList())
    }
}
