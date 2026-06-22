package kr.ac.ssu.ssutoday.domain.article

import kr.ac.ssu.ssutoday.core.exception.BusinessException
import kr.ac.ssu.ssutoday.core.status.SsuStatus
import kr.ac.ssu.ssutoday.domain.article.factory.toView
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.sql.Timestamp

@Service
class ArticleService(
    private val repository: ArticleRepository,
) {
    fun search(providers: List<String>, search: String, pageable: Pageable): Page<ArticleView> =
        repository.search(providers, search, pageable).map(Article::toView)

    fun get(id: Long): ArticleView =
        repository.findById(id)
            .orElseThrow { BusinessException(SsuStatus.SSU4080, arrayOf(id)) }
            .toView()

    fun upsert(
        provider: String,
        articleNo: String,
        title: String,
        content: String?,
        url: String,
        createdAt: Timestamp,
    ): ArticleUpsertResult {
        val existing = repository.findByArticleNoAndProvider(articleNo, provider)
        if (existing != null) {
            existing.update(title, content)
            return ArticleUpsertResult(existing.toView(), false)
        }

        val saved = repository.save(
            Article(
                provider = provider,
                articleNo = articleNo,
                title = title,
                content = content,
                url = url,
                createdAt = createdAt,
            ),
        )
        return ArticleUpsertResult(saved.toView(), true)
    }
}
