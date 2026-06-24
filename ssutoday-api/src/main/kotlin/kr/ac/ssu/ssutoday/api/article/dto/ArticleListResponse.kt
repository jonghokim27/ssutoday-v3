package kr.ac.ssu.ssutoday.api.article.dto

import kr.ac.ssu.ssutoday.application.article.dto.ArticleSummary

data class ArticleListResponse(
    val articles: List<ArticleSummary>,
    val totalPages: Int,
)
