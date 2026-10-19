package kr.ac.ssu.ssutoday.core.turnstile

/** 위젯에 넘기는 action 값. 다른 용도로 발급한 토큰을 돌려쓰지 못하도록 서버에서 일치 여부를 확인한다. */
enum class TurnstileAction(
    val value: String,
) {
    RESERVATION_REQUEST("reservation_request"),
    VERIFY_PHOTO_UPLOAD("verify_photo_upload"),
}
