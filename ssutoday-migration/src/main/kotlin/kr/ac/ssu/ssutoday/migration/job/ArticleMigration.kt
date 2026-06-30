package kr.ac.ssu.ssutoday.migration.job

import io.github.oshai.kotlinlogging.KotlinLogging
import kr.ac.ssu.ssutoday.migration.config.DB
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.BatchPreparedStatementSetter
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.sql.Types

// 구DB에는 `Article`(PascalCase)과 `article`(lowercase) 두 테이블이 존재함.
// `Article`이 원본 테이블이므로 해당 테이블에서만 읽어옴.
// provider 필터: ssucatch, cse
@Component
class ArticleMigration(
    @Qualifier(DB.OLD_JDBC) private val oldDb: JdbcTemplate,
    @Qualifier(DB.NEW_JDBC) private val newDb: JdbcTemplate,
    @Value("\${migration.batch-size:500}") private val batchSize: Int,
) : TableMigration {
    private val log = KotlinLogging.logger {}

    override fun migrate() {
        val rows = oldDb.queryForList(
            "SELECT idx, provider, articleNo, title, content, url, createdAt, updatedAt FROM `Article` WHERE provider IN ('ssucatch', 'cse')",
        )
        log.info { "[article] ${rows.size}건 마이그레이션 시작" }

        rows.chunked(batchSize).forEach { chunk ->
            newDb.batchUpdate(
                "INSERT IGNORE INTO article (id, provider, article_no, title, content, url, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                object : BatchPreparedStatementSetter {
                    override fun setValues(
                        ps: PreparedStatement,
                        i: Int,
                    ) {
                        val row = chunk[i]
                        ps.setLong(1, (row["idx"] as Int).toLong())
                        ps.setString(2, row["provider"] as String)
                        ps.setString(3, row["articleNo"] as String)
                        ps.setString(4, row["title"] as String)
                        val content = row["content"] as? String
                        if (content != null) ps.setString(5, content) else ps.setNull(5, Types.LONGNVARCHAR)
                        ps.setString(6, row["url"] as String)
                        ps.setTimestamp(7, row["createdAt"] as Timestamp)
                        val updatedAt = row["updatedAt"] as? Timestamp
                        if (updatedAt != null) ps.setTimestamp(8, updatedAt) else ps.setNull(8, Types.TIMESTAMP)
                    }

                    override fun getBatchSize(): Int = chunk.size
                },
            )
        }

        log.info { "[article] 완료" }
    }
}
