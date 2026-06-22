package kr.ac.ssu.ssutoday.batch.crawler.base

import kr.ac.ssu.ssutoday.batch.crawler.dto.CrawledArticle

interface ArticleCrawler {
    val provider: String

    fun crawl(): List<CrawledArticle>
}
