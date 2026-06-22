package kr.ac.ssu.ssutoday.batch.crawler

import kr.ac.ssu.ssutoday.application.article.ArticleApplicationService
import kr.ac.ssu.ssutoday.batch.crawler.base.ArticleCrawler
import kr.ac.ssu.ssutoday.batch.crawler.dto.CrawledArticle
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.sql.Timestamp
import kotlin.test.Test

class ArticleCrawlJobTest {
    @Test
    fun `한 크롤러가 실패해도 다른 크롤러의 기사는 저장한다`() {
        val createdAt = Timestamp.valueOf("2026-06-22 12:00:00")
        val article = CrawledArticle(
            provider = "success",
            articleNo = "1",
            title = "제목",
            content = "본문",
            url = "https://example.com/1",
            createdAt = createdAt,
        )
        val failingCrawler = stubCrawler("failure") { error("crawl failed") }
        val successfulCrawler = stubCrawler("success") { listOf(article) }
        val articleApplicationService = mock(ArticleApplicationService::class.java)
        val job = ArticleCrawlJob(
            crawlers = listOf(failingCrawler, successfulCrawler),
            articleApplicationService = articleApplicationService,
        )

        job.execute()

        verify(articleApplicationService).upsert(
            provider = article.provider,
            articleNo = article.articleNo,
            title = article.title,
            content = article.content,
            url = article.url,
            createdAt = createdAt,
        )
    }

    private fun stubCrawler(
        provider: String,
        crawl: () -> List<CrawledArticle>,
    ) = object : ArticleCrawler {
        override val provider = provider

        override fun crawl() = crawl()
    }
}
