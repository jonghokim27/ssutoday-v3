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
import java.sql.Types

@Component
@Order(9)
class ReservationRequestMigration(
    @Qualifier(DB.OLD_JDBC) private val oldDb: JdbcTemplate,
    @Qualifier(DB.NEW_JDBC) private val newDb: JdbcTemplate,
    @Value("\${migration.batch-size:500}") private val batchSize: Int,
) : TableMigration {
    private val log = KotlinLogging.logger {}

    override fun migrate() {
        val rows = oldDb.queryForList(
            "SELECT idx, StudentId, roomNo, date, startBlock, endBlock, status, createdAt, updatedAt FROM `ReserveRequest`",
        )
        log.info { "[reservation_request] ${rows.size}건 마이그레이션 시작" }

        rows.chunked(batchSize).forEach { chunk ->
            newDb.batchUpdate(
                "INSERT IGNORE INTO reservation_request (id, student_id, room_no, date, start_block, end_block, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
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
                        ps.setInt(7, row["status"] as Int)
                        ps.setTimestamp(8, row.timestamp("createdAt"))
                        val updatedAt = row.nullableTimestamp("updatedAt")
                        if (updatedAt != null) ps.setTimestamp(9, updatedAt) else ps.setNull(9, Types.TIMESTAMP)
                    }

                    override fun getBatchSize(): Int = chunk.size
                },
            )
        }

        log.info { "[reservation_request] 완료" }
    }
}
