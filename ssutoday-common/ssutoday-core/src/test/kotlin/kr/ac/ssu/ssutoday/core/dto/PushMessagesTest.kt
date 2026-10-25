package kr.ac.ssu.ssutoday.core.dto

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class PushMessagesTest {
    @Test
    fun `푸시 알림의 날짜는 앱 화면처럼 월과 일을 두 자리로 맞춘다`() {
        assertEquals("2026년 11월 03일(화) 10:00 ~ 12:00", PushMessages.reserveConfirmBody(LocalDate.of(2026, 11, 3), 20, 23))
        assertEquals(
            "11월 03일(화) 스터디룸 1 이용 건의 인증샷이 스터디룸 외부에서 촬영된 것으로 확인되었어요",
            PushMessages.penaltyGrantBody(LocalDate.of(2026, 11, 3), "스터디룸 1"),
        )
        assertEquals(
            "부정 예약이 확인되었어요. 2027년 02월 08일(월)까지 신규 예약이 제한돼요. (사유: 매크로 등 자동화 프로그램 이용)",
            PushMessages.suspensionBody(false, LocalDate.of(2027, 2, 8), "매크로 등 자동화 프로그램 이용"),
        )
        assertEquals("11월 05일(목)에 부여된 페널티가 취소되었어요", PushMessages.penaltyRevokeBody(LocalDate.of(2026, 11, 5)))
    }
}
