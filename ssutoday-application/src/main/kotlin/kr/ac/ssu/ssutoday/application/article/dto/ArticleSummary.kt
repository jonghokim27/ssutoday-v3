package kr.ac.ssu.ssutoday.application.article.dto

import kr.ac.ssu.ssutoday.domain.article.ArticleView
import java.sql.Timestamp

data class ArticleSummary(
    val idx: Long,
    val provider: String,
    val articleNo: String,
    val title: String,
    val content: String?,
    val url: String,
    val createdAt: Timestamp,
    val updatedAt: Timestamp?,
    val starred: Boolean,
)

fun ArticleView.toSummary(starred: Boolean) =
    ArticleSummary(
        idx = idx,
        provider = provider,
        articleNo = articleNo,
        title = title,
        content = content,
        url = url,
        createdAt = createdAt,
        updatedAt = updatedAt,
        starred = starred,
    )
