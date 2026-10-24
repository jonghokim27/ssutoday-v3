package kr.ac.ssu.ssutoday.api.reservation.dto

import kr.ac.ssu.ssutoday.application.penalty.dto.PenaltyStatusResult
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class PenaltyStatusResponse(
    /** 유효한 인증샷 페널티 횟수. */
    val photoCount: Int,
    /** 하루 최대 예약 시간(분). 예약 정지 중이면 null이다. */
    val maxDailyMinutes: Int?,
    val suspendedUntil: String?,
    /** 부정 예약으로 정지된 상태인지. */
    val fraud: Boolean,
    /** 만료로 예약 시간이 처음 늘어나는 시점. */
    val nextRelief: PenaltyReliefResponse?,
    val penalties: List<PenaltyResponse>,
) {
    companion object {
        private val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

        fun format(dateTime: LocalDateTime): String = dateTime.format(DATE_TIME)

        fun from(result: PenaltyStatusResult) =
            PenaltyStatusResponse(
                photoCount = result.photoCount,
                maxDailyMinutes = result.maxDailyBlocks?.let { it * 30 },
                suspendedUntil = result.suspendedUntil?.let(::format),
                fraud = result.fraud,
                nextRelief = result.nextRelief?.let { PenaltyReliefResponse(format(it.at), it.maxDailyBlocks * 30) },
                penalties = result.penalties.map(PenaltyResponse::from),
            )
    }
}
