package kr.ac.ssu.ssutoday.domain.penalty

import kr.ac.ssu.ssutoday.core.penalty.PenaltyReason
import kr.ac.ssu.ssutoday.core.penalty.PenaltyType
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PenaltyPolicyTest {
    private val policy = PenaltyPolicy("2026-11-01")
    private val now = LocalDateTime.of(2026, 11, 17, 12, 0)

    @Test
    fun `유효한 인증샷 페널티 횟수에 따라 하루 최대 예약 시간이 줄어든다`() {
        mapOf(0 to 6, 1 to 6, 2 to 4, 3 to 2, 4 to 2, 5 to null, 8 to null).forEach { (count, blocks) ->
            assertEquals(blocks, policy.maxDailyBlocks(count), "count=$count")
        }
    }

    @Test
    fun `인증샷 페널티는 30일, 부정 예약은 90일 뒤 만료된다`() {
        assertEquals(now.plusDays(30), policy.expiresAt(PenaltyType.PHOTO, now))
        assertEquals(LocalDateTime.of(2027, 2, 15, 12, 0), policy.expiresAt(PenaltyType.FRAUD, now))
    }

    @Test
    fun `취소했거나 만료된 페널티는 세지 않는다`() {
        val standing =
            policy.standing(
                listOf(
                    photo(expiresAt = now.plusDays(3)),
                    photo(expiresAt = now.plusDays(10)),
                    photo(expiresAt = now),
                    photo(expiresAt = now.plusDays(20), revokedAt = now.minusDays(1)),
                ),
                now,
            )
        assertEquals(2, standing.photoCount)
        assertEquals(4, standing.maxDailyBlocks)
        assertNull(standing.suspendedUntil)
    }

    @Test
    fun `다음 만료로 예약 시간이 늘어나는 시점을 알려준다`() {
        val two = policy.standing(listOf(photo(now.plusDays(18)), photo(now.plusDays(23))), now)
        assertEquals(PenaltyRelief(now.plusDays(18), 6), two.nextRelief)

        // 4회에서 1회 만료돼도 3회라 1시간 그대로다. 두 번째 만료에서 2시간이 된다.
        val four = policy.standing((1..4).map { photo(now.plusDays(it.toLong())) }, now)
        assertEquals(PenaltyRelief(now.plusDays(2), 4), four.nextRelief)

        assertNull(policy.standing(listOf(photo(now.plusDays(5))), now).nextRelief)
        assertNull(policy.standing(emptyList(), now).nextRelief)
    }

    @Test
    fun `5회 이상 누적되면 4회로 내려가는 만료 시각까지 예약이 정지된다`() {
        val standing = policy.standing((1..6).map { photo(now.plusDays(it.toLong())) }, now)
        assertEquals(6, standing.photoCount)
        assertNull(standing.maxDailyBlocks)
        assertEquals(now.plusDays(2), standing.suspendedUntil)
        assertFalse(standing.fraud)
        assertEquals(PenaltyRelief(now.plusDays(2), 2), standing.nextRelief)
    }

    @Test
    fun `부정 예약은 횟수와 별개로 만료 시각까지 예약을 정지한다`() {
        val standing = policy.standing(listOf(photo(now.plusDays(5)), fraud(now.plusDays(90))), now)
        assertEquals(1, standing.photoCount)
        assertNull(standing.maxDailyBlocks)
        assertEquals(now.plusDays(90), standing.suspendedUntil)
        assertTrue(standing.fraud)
        assertNull(standing.nextRelief)
    }

    @Test
    fun `부여 미리보기는 지금 부여한 페널티를 더해 계산한다`() {
        val penalties = listOf(photo(now.plusDays(5)))
        assertEquals(4, policy.standingAfterGrant(penalties, PenaltyType.PHOTO, now).maxDailyBlocks)
        assertEquals(now.plusDays(90), policy.standingAfterGrant(penalties, PenaltyType.FRAUD, now).suspendedUntil)
    }

    @Test
    fun `인증샷 페널티는 시행일 이후 이용하고 종료 후 7일 이내인 예약에만 부여한다`() {
        val endAt = LocalDateTime.of(2026, 11, 3, 12, 0)
        val date = endAt.toLocalDate()
        assertNull(policy.grantRejection(PenaltyType.PHOTO, date, endAt, false, endAt.plusDays(7)))
        assertEquals(
            PenaltyGrantRejection.DEADLINE_PASSED,
            policy.grantRejection(PenaltyType.PHOTO, date, endAt, false, endAt.plusDays(7).plusSeconds(1)),
        )
        assertEquals(
            PenaltyGrantRejection.NOT_IN_EFFECT,
            policy.grantRejection(PenaltyType.PHOTO, LocalDate.of(2026, 10, 31), endAt, false, endAt),
        )
        assertEquals(
            PenaltyGrantRejection.ALREADY_GRANTED,
            policy.grantRejection(PenaltyType.PHOTO, date, endAt, true, endAt),
        )
    }

    @Test
    fun `부정 예약은 시행일과 기한 없이 부여한다`() {
        val endAt = LocalDateTime.of(2026, 10, 20, 12, 0)
        assertNull(policy.grantRejection(PenaltyType.FRAUD, endAt.toLocalDate(), endAt, false, endAt.plusDays(30)))
        assertTrue(policy.applies(PenaltyType.FRAUD, LocalDate.of(2026, 10, 1)))
        assertFalse(policy.applies(PenaltyType.PHOTO, LocalDate.of(2026, 10, 31)))
        assertTrue(policy.applies(PenaltyType.PHOTO, LocalDate.of(2026, 11, 1)))
    }

    private fun photo(
        expiresAt: LocalDateTime,
        revokedAt: LocalDateTime? = null,
    ) = view(PenaltyReason.PHOTO_MISSING, expiresAt, revokedAt)

    private fun fraud(expiresAt: LocalDateTime) = view(PenaltyReason.FRAUD_AUTOMATION, expiresAt, null)

    private fun view(
        reason: PenaltyReason,
        expiresAt: LocalDateTime,
        revokedAt: LocalDateTime?,
    ) = PenaltyView(
        id = 1,
        studentId = 20260000,
        reservationId = 1,
        type = reason.type,
        reason = reason,
        grantedBy = "시스템",
        grantedAt = now.minusDays(1),
        expiresAt = expiresAt,
        adminToken = "token",
        revokedAt = revokedAt,
        revokedBy = null,
        revokeReason = null,
    )
}
