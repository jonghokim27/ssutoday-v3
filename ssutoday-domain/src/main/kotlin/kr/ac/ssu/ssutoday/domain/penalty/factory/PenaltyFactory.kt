package kr.ac.ssu.ssutoday.domain.penalty.factory

import kr.ac.ssu.ssutoday.core.penalty.PenaltyReason
import kr.ac.ssu.ssutoday.core.penalty.PenaltyType
import kr.ac.ssu.ssutoday.domain.penalty.Penalty
import kr.ac.ssu.ssutoday.domain.penalty.PenaltyView

fun Penalty.toView() =
    PenaltyView(
        id = id,
        studentId = studentId,
        reservationId = reservationId,
        type = PenaltyType.valueOf(type),
        reason = PenaltyReason.valueOf(reason),
        grantedBy = grantedBy,
        grantedAt = grantedAt.toLocalDateTime(),
        expiresAt = expiresAt.toLocalDateTime(),
        adminToken = adminToken,
        revokedAt = revokedAt?.toLocalDateTime(),
        revokedBy = revokedBy,
        revokeReason = revokeReason,
    )
