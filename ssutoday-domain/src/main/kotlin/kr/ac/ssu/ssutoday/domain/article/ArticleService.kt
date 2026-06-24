package kr.ac.ssu.ssutoday.domain.article

import kr.ac.ssu.ssutoday.core.exception.BusinessException
import kr.ac.ssu.ssutoday.core.status.StatusCode
import kr.ac.ssu.ssutoday.domain.article.factory.toView
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.sql.Timestamp

@Service
class ArticleService(
    private val repository: ArticleRepository,
    private val starRepository: ArticleStarRepository,
) {
    fun search(
        providers: List<String>,
        search: String,
        pageable: Pageable,
    ): Page<ArticleView> = repository.search(providers, search, pageable).map(Article::toView)

    fun searchStarred(
        studentId: Int,
        providers: List<String>,
        search: String,
        pageable: Pageable,
    ): Page<ArticleView> {
        val articleIds = findStarredArticleIds(studentId)
        if (articleIds.isEmpty()) return PageImpl(emptyList(), pageable, 0)
        return repository.searchByIds(articleIds, providers, search, pageable).map(Article::toView)
    }

    fun get(id: Long): ArticleView =
        (
            repository.findByIdOrNull(id)
                ?: throw BusinessException(StatusCode.SSU4080, arrayOf(id))
        ).toView()

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

        val saved =
            repository.save(
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

    fun findStarredArticleIds(studentId: Int): List<Long> = starRepository.findArticleIdsByStudentId(studentId)

    fun countStarredArticles(studentId: Int): Long = starRepository.countByStudentId(studentId)

    fun star(
        studentId: Int,
        articleId: Long,
    ) {
        if (!repository.existsById(articleId)) throw BusinessException(StatusCode.SSU4080, arrayOf(articleId))
        if (starRepository.existsByStudentIdAndArticleId(studentId, articleId)) return
        starRepository.save(ArticleStar(studentId = studentId, articleId = articleId))
    }

    fun unstar(
        studentId: Int,
        articleId: Long,
    ) {
        starRepository.deleteByStudentIdAndArticleId(studentId, articleId)
    }
}
