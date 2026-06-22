package kr.ac.ssu.ssutoday.application.article.dto

import kr.ac.ssu.ssutoday.domain.article.ArticleView

data class ArticlePageResult(
    val articles: List<ArticleView>,
    val totalPages: Int,
)
