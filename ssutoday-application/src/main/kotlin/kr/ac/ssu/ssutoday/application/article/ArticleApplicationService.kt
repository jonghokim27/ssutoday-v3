package kr.ac.ssu.ssutoday.application.article

import kr.ac.ssu.ssutoday.application.article.dto.ArticlePageResult
import kr.ac.ssu.ssutoday.application.article.dto.ArticleQuery
import kr.ac.ssu.ssutoday.core.dto.PushMessage
import kr.ac.ssu.ssutoday.core.port.PushMessagePublisher
import kr.ac.ssu.ssutoday.core.transaction.afterCommit
import kr.ac.ssu.ssutoday.domain.article.ArticleService
import kr.ac.ssu.ssutoday.domain.article.ArticleView
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant

@Service
class ArticleApplicationService(
    private val articleService: ArticleService,
    private val pushMessagePublisher: PushMessagePublisher,
) {
    @Transactional(readOnly = true)
    fun list(query: ArticleQuery): ArticlePageResult {
        val sort = Sort.by("createdAt", "id").let { if (query.ascending) it.ascending() else it.descending() }
        val page = articleService.search(query.providers, "%${query.search}%", PageRequest.of(query.page, 20, sort))
        return ArticlePageResult(page.content, page.totalPages)
    }

    @Transactional(readOnly = true)
    fun get(id: Long): ArticleView = articleService.get(id)

    @Transactional
    fun upsert(
        provider: String,
        articleNo: String,
        title: String,
        content: String?,
        url: String,
        createdAt: Timestamp,
    ) {
        val result = articleService.upsert(provider, articleNo, title, content, url, createdAt)
        if (!result.created) return
        if (Duration.between(createdAt.toInstant(), Instant.now()).abs() >= Duration.ofHours(1)) return

        val article = result.article
        val topic = if (article.provider in setOf("ssucatch", "stu")) "all" else article.provider
        afterCommit {
            pushMessagePublisher.publish(
                PushMessage(
                    "topic",
                    topic = topic,
                    title = "새 공지사항이 등록되었어요!",
                    body = "[${article.provider}] ${article.title}",
                    link = "ssutoday://notice/${article.idx}",
                ),
            )
        }
    }
}
