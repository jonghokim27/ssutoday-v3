package kr.ac.ssu.ssutoday.application.article.dto

data class ArticlePageResult(
    val articles: List<ArticleSummary>,
    val totalPages: Int,
)
