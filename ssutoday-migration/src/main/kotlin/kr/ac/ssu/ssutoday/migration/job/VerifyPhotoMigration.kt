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

@Component
@Order(10)
class VerifyPhotoMigration(
    @Qualifier(DB.OLD_JDBC) private val oldDb: JdbcTemplate,
    @Qualifier(DB.NEW_JDBC) private val newDb: JdbcTemplate,
    @Value("\${migration.batch-size:500}") private val batchSize: Int,
) : TableMigration {
    private val log = KotlinLogging.logger {}

    override fun migrate() {
        val rows = oldDb.queryForList(
            "SELECT idx, ReserveIdx, url, createdAt FROM `VerifyPhoto`",
        )
        log.info { "[verify_photo] ${rows.size}건 마이그레이션 시작" }

        rows.chunked(batchSize).forEach { chunk ->
            newDb.batchUpdate(
                "INSERT IGNORE INTO verify_photo (id, reservation_id, url, created_at) VALUES (?, ?, ?, ?)",
                object : BatchPreparedStatementSetter {
                    override fun setValues(
                        ps: PreparedStatement,
                        i: Int,
                    ) {
                        val row = chunk[i]
                        ps.setLong(1, (row["idx"] as Int).toLong())
                        ps.setLong(2, (row["ReserveIdx"] as Int).toLong())
                        ps.setString(3, row["url"] as String)
                        ps.setTimestamp(4, row.timestamp("createdAt"))
                    }

                    override fun getBatchSize(): Int = chunk.size
                },
            )
        }

        log.info { "[verify_photo] 완료" }
    }
}
