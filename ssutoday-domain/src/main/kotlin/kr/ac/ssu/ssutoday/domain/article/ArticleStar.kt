package kr.ac.ssu.ssutoday.domain.article

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.sql.Timestamp

@Entity
@Table(
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["student_id", "article_id"]),
    ],
)
class ArticleStar(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column
    var id: Long = 0L,
    @Column(nullable = false)
    var studentId: Int,
    @Column(nullable = false)
    var articleId: Long,
    @Column(nullable = false)
    var createdAt: Timestamp = Timestamp(System.currentTimeMillis()),
)
