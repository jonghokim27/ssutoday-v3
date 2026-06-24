package kr.ac.ssu.ssutoday.api.article.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Pattern

data class ArticleListRequest(
    @field:Min(0) val page: Int,
    @field:Pattern(regexp = "ASC|DESC") val orderBy: String,
    val search: String,
    val provider: List<@NotEmpty String>,
    val starredOnly: Boolean = false,
)
