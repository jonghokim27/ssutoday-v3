package kr.ac.ssu.ssutoday.domain.penalty

import kr.ac.ssu.ssutoday.core.penalty.PenaltyType
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime

@Component
class PenaltyPolicy(
    @Value("\${ssutoday.penalty.photo-start-date}")
    photoStartDate: String,
    @Value("\${ssutoday.penalty.target-majors}")
    targetMajors: String,
) {
    private val photoStartDate: LocalDate = LocalDate.parse(photoStartDate)
    private val targetMajors: Set<String> =
        targetMajors
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()

    init {
        require(this.targetMajors.isNotEmpty()) { "ssutoday.penalty.target-majors must not be empty" }
    }

    /** 페널티 대상 학과의 학생인지. 대상이 아니면 어떤 페널티도 부여하지 않는다. */
    fun targets(major: String): Boolean = major in targetMajors

    fun expiresAt(
        type: PenaltyType,
        grantedAt: LocalDateTime,
    ): LocalDateTime =
        when (type) {
            PenaltyType.PHOTO -> grantedAt.plusDays(PHOTO_VALID_DAYS)
            PenaltyType.FRAUD -> grantedAt.plusDays(FRAUD_VALID_DAYS)
        }

    /** 인증샷 페널티는 시행일 이후 이용한 예약에만 부여한다. 부정 예약은 공지 즉시 적용한다. */
    fun applies(
        type: PenaltyType,
        reservationDate: LocalDate,
    ): Boolean = type == PenaltyType.FRAUD || !reservationDate.isBefore(photoStartDate)

    /** 관리자 부여는 이용 종료 후 기한 없이 할 수 있다. */
    fun grantRejection(
        major: String,
        type: PenaltyType,
        reservationDate: LocalDate,
        alreadyGranted: Boolean,
    ): PenaltyGrantRejection? {
        if (!targets(major)) return PenaltyGrantRejection.NOT_TARGET
        if (alreadyGranted) return PenaltyGrantRejection.ALREADY_GRANTED
        if (!applies(type, reservationDate)) return PenaltyGrantRejection.NOT_IN_EFFECT
        return null
    }

    /** 유효한 인증샷 페널티 횟수에 따른 하루 최대 예약 블록 수. 예약 정지면 null이다. */
    fun maxDailyBlocks(photoCount: Int): Int? =
        when {
            photoCount <= 1 -> 6
            photoCount == 2 -> 4
            photoCount < SUSPEND_COUNT -> 2
            else -> null
        }

    fun standing(
        penalties: Collection<PenaltyView>,
        now: LocalDateTime,
    ): PenaltyStanding = standingOf(validExpiries(penalties, now))

    /** 지금 type 페널티를 하나 더 부여했을 때의 상태. 관리자 화면 미리보기에 쓴다. */
    fun standingAfterGrant(
        penalties: Collection<PenaltyView>,
        type: PenaltyType,
        now: LocalDateTime,
    ): PenaltyStanding = standingOf(validExpiries(penalties, now) + (type to expiresAt(type, now)))

    private fun validExpiries(
        penalties: Collection<PenaltyView>,
        now: LocalDateTime,
    ): List<Pair<PenaltyType, LocalDateTime>> = penalties.filter { it.validAt(now) }.map { it.type to it.expiresAt }

    private fun standingOf(valid: List<Pair<PenaltyType, LocalDateTime>>): PenaltyStanding {
        val fraudUntil = valid.filter { it.first == PenaltyType.FRAUD }.maxOfOrNull { it.second }
        val photoExpiries = valid.filter { it.first == PenaltyType.PHOTO }.map { it.second }.sorted()
        val photoCount = photoExpiries.size
        // 누적 정지는 남은 횟수가 정지 기준 아래로 내려가는 만료 시각에 풀린다.
        val accumulatedUntil = if (photoCount >= SUSPEND_COUNT) photoExpiries[photoCount - SUSPEND_COUNT] else null
        val suspendedUntil = listOfNotNull(fraudUntil, accumulatedUntil).maxOrNull()
        return PenaltyStanding(
            photoCount = photoCount,
            maxDailyBlocks = if (suspendedUntil == null) maxDailyBlocks(photoCount) else null,
            suspendedUntil = suspendedUntil,
            fraud = fraudUntil != null,
            nextRelief = if (fraudUntil == null) nextRelief(photoExpiries) else null,
        )
    }

    private fun nextRelief(photoExpiries: List<LocalDateTime>): PenaltyRelief? {
        val current = maxDailyBlocks(photoExpiries.size) ?: 0
        photoExpiries.forEachIndexed { index, at ->
            val blocks = maxDailyBlocks(photoExpiries.size - index - 1) ?: 0
            if (blocks > current) return PenaltyRelief(at, blocks)
        }
        return null
    }

    private companion object {
        const val PHOTO_VALID_DAYS = 30L
        const val FRAUD_VALID_DAYS = 90L
        const val SUSPEND_COUNT = 5
    }
}
