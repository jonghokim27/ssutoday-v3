package kr.ac.ssu.ssutoday.domain.article

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import java.sql.Timestamp

@Entity
class Article(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column
    var id: Long = 0L,
    @Column(nullable = false)
    var provider: String,
    @Column(nullable = false, length = 200)
    var articleNo: String,
    @Column(nullable = false, length = 500)
    var title: String,
    @Column(columnDefinition = "TEXT")
    var content: String? = null,
    @Column(nullable = false, length = 200)
    var url: String,
    @Column(nullable = false)
    var createdAt: Timestamp,
    @Column
    var updatedAt: Timestamp? = null,
) {
    fun update(
        title: String,
        content: String?,
    ) {
        if (this.title == title && this.content == content) return
        this.title = title
        this.content = content
        updatedAt = Timestamp(System.currentTimeMillis())
    }
}
