package kr.ac.ssu.ssutoday.domain.article

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ArticleRepository : JpaRepository<Article, Long> {
    fun findByArticleNoAndProvider(articleNo: String, provider: String): Article?

    @Query(
        """
            select a from Article a
            where a.provider in :providers
              and (a.title like :search or a.content like :search)
        """,
    )
    fun search(providers: List<String>, search: String, pageable: Pageable): Page<Article>
}
