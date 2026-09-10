package kr.ac.ssu.ssutoday.core.port

import kr.ac.ssu.ssutoday.core.dto.PhotoInspection

interface VerifyPhotoInspectionPort {
    /**
     * 공개 URL의 인증샷을 내려받아 스터디룸 사진인지 검사한다.
     *
     * 검사 자체가 실패했을 때(미설정, 네트워크 오류, 응답 파싱 실패)는 null을 반환한다.
     * 호출부는 null을 "판정 불가"로 다루고 이용자에게 불이익을 주지 않아야 한다.
     */
    fun inspect(imageUrl: String): PhotoInspection?
}
