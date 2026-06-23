package kr.ac.ssu.ssutoday.batch.crawler.base

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.sql.Timestamp
import java.time.format.DateTimeFormatter

abstract class GnuBoardCrawler(
    provider: String,
    maxPages: Int,
) : JsoupArticleCrawler(provider, maxPages) {
    protected abstract val baseUrl: String

    override fun listUrl(page: Int) = "$baseUrl$page"

    override fun links(document: Document) = document.select(".notice_list > table > tbody > tr a[href*=wr_id]")

    override fun articleNo(link: Element) = parameter(link.attr("href"), "wr_id")

    override fun detailUrl(
        link: Element,
        articleNo: String,
    ) = "$baseUrl&wr_id=$articleNo"

    override fun title(document: Document) = document.requiredText(".bo_v_tit")

    override fun content(document: Document) = document.requiredText("#bo_v_con")

    override fun createdAt(
        link: Element,
        document: Document,
    ): Timestamp =
        ArticleDateParser.parseDateTime(
            document.requiredText("strong.if_date").removePrefix("작성일"),
            DATE_FORMATTER,
        )

    private companion object {
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yy-MM-dd HH:mm")
    }
}
