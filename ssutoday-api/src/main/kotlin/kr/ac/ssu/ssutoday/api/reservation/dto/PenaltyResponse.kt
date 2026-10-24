package kr.ac.ssu.ssutoday.api.reservation.dto

import kr.ac.ssu.ssutoday.application.penalty.dto.PenaltyItem

data class PenaltyResponse(
    val id: Long,
    /** PHOTO 또는 FRAUD */
    val type: String,
    /** PenaltyReason 이름. 화면 문구는 앱이 사유별로 정한다. */
    val reason: String,
    val grantedAt: String,
    val expiresAt: String,
    val roomName: String,
    val date: String,
    val startBlock: Int,
    val endBlock: Int,
    val reservationCancelled: Boolean,
) {
    companion object {
        fun from(item: PenaltyItem) =
            PenaltyResponse(
                id = item.id,
                type = item.type.name,
                reason = item.reason.name,
                grantedAt = PenaltyStatusResponse.format(item.grantedAt),
                expiresAt = PenaltyStatusResponse.format(item.expiresAt),
                roomName = item.roomName,
                date = item.reservationDate.toString(),
                startBlock = item.startBlock,
                endBlock = item.endBlock,
                reservationCancelled = item.reservationCancelled,
            )
    }
}
