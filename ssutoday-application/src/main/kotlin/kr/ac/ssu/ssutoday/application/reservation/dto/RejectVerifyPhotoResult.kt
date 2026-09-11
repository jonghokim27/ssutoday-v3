package kr.ac.ssu.ssutoday.application.reservation.dto

/**
 * 자동 검사 거부 처리 결과다.
 *
 * @param deleteCount 이 예약의 누적 인증샷 삭제 횟수. 첫 삭제면 1이다
 * @param action 실제로 수행한 관리자 액션. 처리하지 않았으면 null이다
 * @param status executeAdminActionByToken이 반환한 상태 코드
 */
data class RejectVerifyPhotoResult(
    val deleteCount: Int,
    val action: String?,
    val status: Int,
)
