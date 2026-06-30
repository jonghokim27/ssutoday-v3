package kr.ac.ssu.ssutoday.migration.job

import io.github.oshai.kotlinlogging.KotlinLogging
import kr.ac.ssu.ssutoday.migration.config.DB
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.BatchPreparedStatementSetter
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.sql.Types

@Component
@Order(1)
class StudentMigration(
    @Qualifier(DB.OLD_JDBC) private val oldDb: JdbcTemplate,
    @Qualifier(DB.NEW_JDBC) private val newDb: JdbcTemplate,
    @Value("\${migration.batch-size:500}") private val batchSize: Int,
) : TableMigration {
    private val log = KotlinLogging.logger {}

    override fun migrate() {
        val rows = oldDb.queryForList(
            "SELECT id, name, major, xnApiToken, isAdmin, createdAt, updatedAt FROM `Student`",
        )
        log.info { "[student] ${rows.size}건 마이그레이션 시작" }

        rows.chunked(batchSize).forEach { chunk ->
            newDb.batchUpdate(
                "INSERT IGNORE INTO student (id, name, major, xn_api_token, is_admin, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                object : BatchPreparedStatementSetter {
                    override fun setValues(
                        ps: PreparedStatement,
                        i: Int,
                    ) {
                        val row = chunk[i]
                        ps.setInt(1, row["id"] as Int)
                        ps.setString(2, row["name"] as String)
                        ps.setString(3, row["major"] as String)
                        val xnApiToken = row["xnApiToken"] as? String
                        if (xnApiToken != null) ps.setString(4, xnApiToken) else ps.setNull(4, Types.VARCHAR)
                        ps.setInt(5, row["isAdmin"] as Int)
                        ps.setTimestamp(6, row["createdAt"] as Timestamp)
                        val updatedAt = row["updatedAt"] as? Timestamp
                        if (updatedAt != null) ps.setTimestamp(7, updatedAt) else ps.setNull(7, Types.TIMESTAMP)
                    }

                    override fun getBatchSize(): Int = chunk.size
                },
            )
        }

        log.info { "[student] 완료" }
    }
}
