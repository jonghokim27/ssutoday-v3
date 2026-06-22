package kr.ac.ssu.ssutoday.api.article.dto

import jakarta.validation.constraints.Min

data class ArticleIdRequest(
    @field:Min(1) val idx: Long,
)
