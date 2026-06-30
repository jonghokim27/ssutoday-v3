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
@Order(3)
class SsoClientMigration(
    @Qualifier(DB.OLD_JDBC) private val oldDb: JdbcTemplate,
    @Qualifier(DB.NEW_JDBC) private val newDb: JdbcTemplate,
    @Value("\${migration.batch-size:500}") private val batchSize: Int,
) : TableMigration {
    private val log = KotlinLogging.logger {}

    override fun migrate() {
        val rows = oldDb.queryForList(
            "SELECT id, secret, callbackUrl FROM `SSOClient`",
        )
        log.info { "[sso_client] ${rows.size}건 마이그레이션 시작" }

        rows.chunked(batchSize).forEach { chunk ->
            newDb.batchUpdate(
                "INSERT IGNORE INTO sso_client (id, secret, callback_url) VALUES (?, ?, ?)",
                object : BatchPreparedStatementSetter {
                    override fun setValues(
                        ps: PreparedStatement,
                        i: Int,
                    ) {
                        val row = chunk[i]
                        ps.setString(1, row["id"] as String)
                        ps.setString(2, row["secret"] as String)
                        ps.setString(3, row["callbackUrl"] as String)
                    }

                    override fun getBatchSize(): Int = chunk.size
                },
            )
        }

        log.info { "[sso_client] 완료" }
    }
}
