package kr.ac.ssu.ssutoday.batch.crawler.dto

import java.sql.Timestamp
import java.time.Instant

data class CrawledArticle(
    val provider: String,
    val articleNo: String,
    val title: String,
    val content: String,
    val url: String,
    val createdAt: Timestamp = Timestamp.from(Instant.now()),
)
