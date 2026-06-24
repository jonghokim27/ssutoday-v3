package kr.ac.ssu.ssutoday.application.article.dto

data class ArticleQuery(
    val studentId: Int,
    val page: Int,
    val ascending: Boolean,
    val search: String,
    val providers: List<String>,
    val starredOnly: Boolean,
)
