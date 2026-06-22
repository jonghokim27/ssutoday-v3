package kr.ac.ssu.ssutoday.batch.crawler

import kr.ac.ssu.ssutoday.batch.crawler.base.GnuBoardCrawler
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class CseCrawler(
    @Value("\${ssutoday.crawler.max-pages:2}") pages: Int,
) : GnuBoardCrawler("cse", pages) {
    override val baseUrl = "https://cse.ssu.ac.kr/bbs/board.php?bo_table=notice&page="
}
