package kr.ac.ssu.ssutoday.core.dto

/**
 * 인증샷이 실제 스터디룸에서 촬영되었는지에 대한 자동 검사 결과다.
 *
 * @param isStudyRoom 스터디룸 인증샷으로 인정할 수 있으면 true
 * @param confidence 판정 확신도. 0에 가까울수록 애매하다
 * @param reason 판정 근거 한 문장. 관리자 알림에 그대로 노출한다
 */
data class PhotoInspection(
    val isStudyRoom: Boolean,
    val confidence: Double,
    val reason: String,
)
