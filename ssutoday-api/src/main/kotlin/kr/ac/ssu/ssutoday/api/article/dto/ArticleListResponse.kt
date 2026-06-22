package kr.ac.ssu.ssutoday.api.article.dto

import kr.ac.ssu.ssutoday.domain.article.ArticleView

data class ArticleListResponse(
    val articles: List<ArticleView>,
    val totalPages: Int,
)
