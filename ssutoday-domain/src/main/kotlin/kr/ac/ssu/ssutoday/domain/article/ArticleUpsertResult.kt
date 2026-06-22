package kr.ac.ssu.ssutoday.domain.article

data class ArticleUpsertResult(
    val article: ArticleView,
    val created: Boolean,
)
