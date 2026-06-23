package kr.ac.ssu.ssutoday.batch.crawler

import kr.ac.ssu.ssutoday.batch.crawler.base.ArticleCrawler
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import tools.jackson.databind.ObjectMapper
import kotlin.test.assertTrue

class ArticleCrawlerIntegrationTest {
    @TestFactory
    fun `실제 공지사항을 크롤링한다`(): List<DynamicTest> =
        crawlers().map { crawler ->
            DynamicTest.dynamicTest(crawler.provider) {
                val articles = crawler.crawl()

                assertTrue(articles.isNotEmpty(), "${crawler.provider} 크롤링 결과가 없습니다")
                articles.forEach { article ->
                    assertTrue(article.provider == crawler.provider)
                    assertTrue(article.articleNo.isNotBlank())
                    assertTrue(article.title.isNotBlank())
                    assertTrue(article.url.isNotBlank())
                }
            }
        }

    private fun crawlers(): List<ArticleCrawler> =
        listOf(
            CseCrawler(1),
            SsuCatchCrawler(1),
            StudentCouncilCrawler(
                pages = 1,
                objectMapper = ObjectMapper(),
            ),
        )
}
