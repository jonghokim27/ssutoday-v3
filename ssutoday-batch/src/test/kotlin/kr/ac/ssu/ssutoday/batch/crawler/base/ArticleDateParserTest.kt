package kr.ac.ssu.ssutoday.batch.crawler.base

import java.sql.Timestamp
import java.time.format.DateTimeFormatter
import kotlin.test.Test
import kotlin.test.assertEquals

class ArticleDateParserTest {
    @Test
    fun `CSE 작성일을 시각까지 파싱한다`() {
        val createdAt =
            ArticleDateParser.parseDateTime(
                "26-05-13 11:32",
                DateTimeFormatter.ofPattern("yy-MM-dd HH:mm"),
            )

        assertEquals(Timestamp.valueOf("2026-05-13 11:32:00"), createdAt)
    }

    @Test
    fun `SSU catch 작성일을 날짜 기준으로 파싱한다`() {
        val createdAt =
            ArticleDateParser.parseDate(
                "2026년 6월 22일",
                DateTimeFormatter.ofPattern("yyyy년 M월 d일"),
            )

        assertEquals(Timestamp.valueOf("2026-06-22 00:00:00"), createdAt)
    }
}
