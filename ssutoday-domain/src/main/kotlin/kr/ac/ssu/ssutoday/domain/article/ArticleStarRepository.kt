package kr.ac.ssu.ssutoday.domain.article

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ArticleStarRepository : JpaRepository<ArticleStar, Long> {
    fun existsByStudentIdAndArticleId(
        studentId: Int,
        articleId: Long,
    ): Boolean

    fun deleteByStudentIdAndArticleId(
        studentId: Int,
        articleId: Long,
    )

    @Query(
        """
            select s.articleId from ArticleStar s
            where s.studentId = :studentId
        """,
    )
    fun findArticleIdsByStudentId(studentId: Int): List<Long>

    fun countByStudentId(studentId: Int): Long
}
