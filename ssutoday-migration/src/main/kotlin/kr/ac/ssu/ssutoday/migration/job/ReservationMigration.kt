package kr.ac.ssu.ssutoday.migration.job

import io.github.oshai.kotlinlogging.KotlinLogging
import kr.ac.ssu.ssutoday.migration.config.DB
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.BatchPreparedStatementSetter
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.sql.PreparedStatement
import java.sql.Types
import java.util.Base64

@Component
@Order(8)
class ReservationMigration(
    @Qualifier(DB.OLD_JDBC) private val oldDb: JdbcTemplate,
    @Qualifier(DB.NEW_JDBC) private val newDb: JdbcTemplate,
    @Value("\${migration.batch-size:500}") private val batchSize: Int,
) : TableMigration {
    private val log = KotlinLogging.logger {}
    private val secureRandom = SecureRandom()

    private fun generateAdminToken(): String {
        val bytes = ByteArray(150)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    override fun migrate() {
        val rows = oldDb.queryForList(
            "SELECT idx, StudentId, roomNo, date, startBlock, endBlock, createdAt, deletedAt, deletedReason FROM `Reserve`",
        )
        log.info { "[reservation] ${rows.size}건 마이그레이션 시작" }

        rows.chunked(batchSize).forEach { chunk ->
            newDb.batchUpdate(
                "INSERT IGNORE INTO reservation (id, student_id, room_no, date, start_block, end_block, created_at, deleted_at, deleted_reason, admin_token) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                object : BatchPreparedStatementSetter {
                    override fun setValues(
                        ps: PreparedStatement,
                        i: Int,
                    ) {
                        val row = chunk[i]
                        ps.setLong(1, (row["idx"] as Int).toLong())
                        ps.setInt(2, row["StudentId"] as Int)
                        ps.setString(3, row["roomNo"] as String)
                        ps.setDate(4, row.date("date"))
                        ps.setInt(5, row["startBlock"] as Int)
                        ps.setInt(6, row["endBlock"] as Int)
                        ps.setTimestamp(7, row.timestamp("createdAt"))
                        val deletedAt = row.nullableTimestamp("deletedAt")
                        if (deletedAt != null) ps.setTimestamp(8, deletedAt) else ps.setNull(8, Types.TIMESTAMP)
                        val deletedReason = row["deletedReason"] as? String
                        if (deletedReason != null) ps.setString(9, deletedReason) else ps.setNull(9, Types.VARCHAR)
                        ps.setString(10, generateAdminToken())
                    }

                    override fun getBatchSize(): Int = chunk.size
                },
            )
        }

        log.info { "[reservation] 완료" }
    }
}
