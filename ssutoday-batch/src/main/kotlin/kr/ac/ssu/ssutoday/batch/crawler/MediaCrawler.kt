package kr.ac.ssu.ssutoday.batch.crawler

import io.github.oshai.kotlinlogging.KotlinLogging
import kr.ac.ssu.ssutoday.batch.crawler.base.ArticleCrawler
import kr.ac.ssu.ssutoday.batch.crawler.dto.CrawledArticle
import org.jsoup.Jsoup
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.sql.Timestamp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Component
class MediaCrawler(
    @Value("\${ssutoday.crawler.max-pages:2}") pages: Int,
    private val objectMapper: ObjectMapper,
) : ArticleCrawler {
    private val log = KotlinLogging.logger {}

    override val provider = "media"
    private val maxPages = pages

    override fun crawl(): List<CrawledArticle> = (maxPages - 1 downTo 0).flatMap(::crawlPage)

    private fun crawlPage(page: Int): List<CrawledArticle> {
        val response =
            Jsoup
                .connect("$API_URL/v1/board/?menuId=$MENU_ID&page=$page&size=$PAGE_SIZE")
                .userAgent(USER_AGENT)
                .ignoreContentType(true)
                .timeout(TIMEOUT_MILLIS)
                .execute()

        val boards = objectMapper.readTree(response.body()).path("data").path("boards")

        return (0 until boards.size()).mapNotNull { i ->
            val board = boards[i]
            runCatching {
                CrawledArticle(
                    provider = provider,
                    articleNo = board.path("id").asText(),
                    title = board.path("title").asText().trim(),
                    content = extractContent(board.path("content").asText()),
                    url = "$SITE_URL/board/notices/${board.path("id").asText()}",
                    createdAt = Timestamp.valueOf(LocalDateTime.parse(board.path("createdAt").asText(), DATE_FORMATTER)),
                )
            }.onFailure {
                log.warn(it) { "Article crawl failed: provider=$provider, id=${board.path("id").asText()}" }
            }.getOrNull()
        }
    }

    private fun extractContent(raw: String): String =
        runCatching {
            // content is double-JSON-encoded: a JSON string containing another JSON string
            val jsonStr = objectMapper.readValue(raw, String::class.java)
            val root = objectMapper.readTree(jsonStr).path("editorState").path("root")
            extractTextFromNode(root).trim()
        }.getOrDefault(raw.trim())

    private fun extractTextFromNode(node: JsonNode): String {
        if (node.isMissingNode || node.isNull) return ""
        if (node.path("type").asText() == "text") return node.path("text").asText()
        val children = node.path("children")
        return (0 until children.size())
            .joinToString(" ") { extractTextFromNode(children[it]) }
            .replace(Regex("\\s+"), " ")
    }

    private companion object {
        const val API_URL = "https://3-37-127-112.sslip.io"
        const val SITE_URL = "https://media.ssu.ac.kr"
        const val MENU_ID = 136
        const val PAGE_SIZE = 20
        const val USER_AGENT = "Mozilla/5.0 SSUtoday crawler"
        const val TIMEOUT_MILLIS = 10_000

        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
