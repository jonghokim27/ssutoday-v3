package kr.ac.ssu.ssutoday.domain.article

import java.sql.Timestamp

data class ArticleView(
    val idx: Long,
    val provider: String,
    val articleNo: String,
    val title: String,
    val content: String?,
    val url: String,
    val createdAt: Timestamp,
    val updatedAt: Timestamp?,
)
