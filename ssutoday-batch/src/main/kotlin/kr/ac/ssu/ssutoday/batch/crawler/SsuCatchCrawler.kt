package kr.ac.ssu.ssutoday.batch.crawler

import kr.ac.ssu.ssutoday.batch.crawler.base.JsoupArticleCrawler
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class SsuCatchCrawler(
    @Value("\${ssutoday.crawler.max-pages:2}") pages: Int,
) : JsoupArticleCrawler("ssucatch", pages) {
    override fun listUrl(page: Int) = "https://scatch.ssu.ac.kr/공지사항/page/$page"
    override fun links(document: Document) = document.select(".notice_col3 > a")
    override fun articleNo(link: Element) = parameter(link.attr("href"), "slug")
    override fun title(document: Document) = document.requiredText("h1.font-weight-light.mb-3")
    override fun content(document: Document) = document.requiredText("div.bg-white.p-4.mb-5 > div:not(.clearfix)")
}
