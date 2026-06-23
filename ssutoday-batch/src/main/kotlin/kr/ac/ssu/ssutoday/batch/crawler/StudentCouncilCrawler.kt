package kr.ac.ssu.ssutoday.batch.crawler

import kr.ac.ssu.ssutoday.batch.crawler.base.ArticleCrawler
import kr.ac.ssu.ssutoday.batch.crawler.dto.CrawledArticle
import kr.ac.ssu.ssutoday.batch.crawler.dto.StudentCouncilResponse
import org.jsoup.Jsoup
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.sql.Timestamp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Component
class StudentCouncilCrawler(
    @Value("\${ssutoday.crawler.max-pages:2}") pages: Int,
    private val objectMapper: ObjectMapper,
) : ArticleCrawler {
    override val provider = "stu"

    private val maxPages = pages

    override fun crawl(): List<CrawledArticle> = (maxPages - 1 downTo 0).flatMap(::crawlPage)

    private fun crawlPage(page: Int): List<CrawledArticle> {
        val response =
            Jsoup
                .connect(listUrl(page))
                .userAgent(USER_AGENT)
                .ignoreContentType(true)
                .timeout(TIMEOUT_MILLIS)
                .execute()
        val posts =
            objectMapper
                .readValue(response.body(), StudentCouncilResponse::class.java)
                .data
                .postListResDto

        return posts.map { post ->
            CrawledArticle(
                provider = provider,
                articleNo = post.postId.toString(),
                title = post.title.trim(),
                content = Jsoup.parse(post.content).text().trim(),
                url = "$SITE_URL/notice/${post.postId}",
                createdAt = Timestamp.valueOf(LocalDateTime.parse(post.date, DATE_FORMATTER)),
            )
        }
    }

    private fun listUrl(page: Int): String {
        val boardCode = encode("공지사항게시판")
        val groupCode = encode("중앙기구")
        return "$API_URL/board/$boardCode/posts/search?page=$page&take=$PAGE_SIZE" +
            "&groupCode=$groupCode&memberCode=&q="
    }

    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private companion object {
        const val API_URL = "https://backend.sssupport.shop"
        const val SITE_URL = "https://stu.ssu.ac.kr"
        const val PAGE_SIZE = 16
        const val USER_AGENT = "Mozilla/5.0 SSUtoday crawler"
        const val TIMEOUT_MILLIS = 10_000

        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
    }
}
