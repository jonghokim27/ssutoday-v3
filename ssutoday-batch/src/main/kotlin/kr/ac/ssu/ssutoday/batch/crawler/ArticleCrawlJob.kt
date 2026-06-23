package kr.ac.ssu.ssutoday.batch.crawler

import io.github.oshai.kotlinlogging.KotlinLogging
import kr.ac.ssu.ssutoday.application.article.ArticleApplicationService
import kr.ac.ssu.ssutoday.batch.crawler.base.ArticleCrawler
import kr.ac.ssu.ssutoday.batch.crawler.dto.CrawledArticle
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ArticleCrawlJob(
    private val crawlers: List<ArticleCrawler>,
    private val articleApplicationService: ArticleApplicationService,
) {
    private val log = KotlinLogging.logger {}

    @Scheduled(fixedDelayString = "\${ssutoday.crawler.fixed-delay:60000}")
    fun execute() {
        crawlers.forEach { crawler ->
            runCatching { crawler.crawl() }
                .onFailure { log.error(it) { "Crawler failed: ${crawler.provider}" } }
                .getOrDefault(emptyList())
                .forEach(::upsert)
        }
    }

    private fun upsert(crawled: CrawledArticle) {
        articleApplicationService.upsertArticle(
            provider = crawled.provider,
            articleNo = crawled.articleNo,
            title = crawled.title,
            content = crawled.content,
            url = crawled.url,
            createdAt = crawled.createdAt,
        )
    }
}
