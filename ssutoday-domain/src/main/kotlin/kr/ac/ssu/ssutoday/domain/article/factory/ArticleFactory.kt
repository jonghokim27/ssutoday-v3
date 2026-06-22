package kr.ac.ssu.ssutoday.domain.article.factory

import kr.ac.ssu.ssutoday.domain.article.Article
import kr.ac.ssu.ssutoday.domain.article.ArticleView

fun Article.toView() = ArticleView(
    idx = id,
    provider = provider,
    articleNo = articleNo,
    title = title,
    content = content,
    url = url,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
