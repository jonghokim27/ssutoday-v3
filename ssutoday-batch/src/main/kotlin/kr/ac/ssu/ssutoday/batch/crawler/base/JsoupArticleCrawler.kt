package kr.ac.ssu.ssutoday.batch.crawler.base

import io.github.oshai.kotlinlogging.KotlinLogging
import kr.ac.ssu.ssutoday.batch.crawler.dto.CrawledArticle
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

abstract class JsoupArticleCrawler(
    final override val provider: String,
    private val maxPages: Int,
) : ArticleCrawler {
    private val log = KotlinLogging.logger {}

    protected abstract fun listUrl(page: Int): String

    protected abstract fun links(document: Document): List<Element>

    protected abstract fun articleNo(link: Element): String

    protected open fun detailUrl(
        link: Element,
        articleNo: String,
    ): String = link.absUrl("href")

    protected abstract fun title(document: Document): String

    protected abstract fun content(document: Document): String

    final override fun crawl(): List<CrawledArticle> =
        (maxPages downTo 1)
            .flatMap(::crawlPage)

    protected fun parameter(
        value: String,
        name: String,
    ): String =
        Regex("""(?:^|[?&])${Regex.escape(name)}=([^&#]+)""")
            .find(value)
            ?.groupValues
            ?.get(1)
            ?.takeIf(String::isNotBlank)
            ?: error("Query parameter '$name' not found: $value")

    protected fun Document.requiredText(selector: String): String =
        requireNotNull(selectFirst(selector)) { "Element not found: $selector" }.text()

    private fun crawlPage(page: Int): List<CrawledArticle> =
        links(load(listUrl(page)))
            .mapNotNull(::crawlArticle)

    private fun crawlArticle(link: Element): CrawledArticle? =
        runCatching {
            val articleNo = articleNo(link)
            val url = detailUrl(link, articleNo)
            val document = load(url)
            CrawledArticle(
                provider = provider,
                articleNo = articleNo,
                title = title(document).trim(),
                content = content(document).replace('\u00a0', ' ').trim(),
                url = url,
            )
        }.onFailure {
            log.warn(it) { "Article crawl failed: provider=$provider, href=${link.attr("href")}" }
        }.getOrNull()

    private fun load(url: String): Document =
        Jsoup
            .connect(url)
            .userAgent(USER_AGENT)
            .timeout(TIMEOUT_MILLIS)
            .get()

    private companion object {
        const val USER_AGENT = "Mozilla/5.0 SSUtoday crawler"
        const val TIMEOUT_MILLIS = 10_000
    }
}
