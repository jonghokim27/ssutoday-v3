package kr.ac.ssu.ssutoday.application.article.dto

data class ArticleQuery(
    val page: Int,
    val ascending: Boolean,
    val search: String,
    val providers: List<String>,
)
