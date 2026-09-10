package kr.ac.ssu.ssutoday.application.reservation.dto

/**
 * 자동 검사 거부 처리 결과다.
 *
 * @param rejectionCount 이 예약의 누적 거부 횟수. 첫 거부면 1이다
 * @param action 실제로 수행한 관리자 액션. 처리하지 않았으면 null이다
 * @param status executeAdminActionByToken이 반환한 상태 코드
 */
data class RejectVerifyPhotoResult(
    val rejectionCount: Int,
    val action: String?,
    val status: Int,
)
