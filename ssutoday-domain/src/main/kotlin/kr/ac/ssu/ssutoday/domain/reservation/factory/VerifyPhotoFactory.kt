package kr.ac.ssu.ssutoday.domain.reservation.factory

import kr.ac.ssu.ssutoday.domain.reservation.VerifyPhoto
import kr.ac.ssu.ssutoday.domain.reservation.VerifyPhotoView

fun VerifyPhoto.toView() = VerifyPhotoView(
    id = id,
    reservationId = reservationId,
    url = url,
    createdAt = createdAt,
)
