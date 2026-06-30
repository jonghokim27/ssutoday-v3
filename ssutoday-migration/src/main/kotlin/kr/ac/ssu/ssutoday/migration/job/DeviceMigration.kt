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

// 구DB는 (StudentId, osType, uuid) UNIQUE 제약이 없어 중복이 존재할 수 있음.
// 신DB는 UNIQUE(student_id, os_type, uuid) 제약이 있으므로 그룹별 MAX(idx)만 가져옴.
@Component
class DeviceMigration(
    @Qualifier(DB.OLD_JDBC) private val oldDb: JdbcTemplate,
    @Qualifier(DB.NEW_JDBC) private val newDb: JdbcTemplate,
    @Value("\${migration.batch-size:500}") private val batchSize: Int,
) : TableMigration {
    private val log = KotlinLogging.logger {}

    override fun migrate() {
        val rows = oldDb.queryForList(
            """
            SELECT d.idx, d.StudentId, d.osType, d.uuid, d.pushToken, d.notice, d.reserve, d.lms, d.createdAt, d.updatedAt
            FROM `Device` d
            INNER JOIN (
                SELECT StudentId, osType, uuid, MAX(idx) AS max_idx
                FROM `Device`
                GROUP BY StudentId, osType, uuid
            ) dedup ON d.StudentId = dedup.StudentId
                AND d.osType = dedup.osType
                AND d.uuid = dedup.uuid
                AND d.idx = dedup.max_idx
            """.trimIndent(),
        )
        log.info { "[device] ${rows.size}건 마이그레이션 시작" }

        rows.chunked(batchSize).forEach { chunk ->
            newDb.batchUpdate(
                "INSERT IGNORE INTO device (id, student_id, os_type, uuid, push_token, notice, reserve, lms, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                object : BatchPreparedStatementSetter {
                    override fun setValues(
                        ps: PreparedStatement,
                        i: Int,
                    ) {
                        val row = chunk[i]
                        ps.setLong(1, (row["idx"] as Int).toLong())
                        ps.setInt(2, row["StudentId"] as Int)
                        ps.setString(3, row["osType"] as String)
                        ps.setString(4, row["uuid"] as String)
                        ps.setString(5, row["pushToken"] as String)
                        ps.setInt(6, row["notice"] as Int)
                        ps.setInt(7, row["reserve"] as Int)
                        ps.setInt(8, row["lms"] as Int)
                        ps.setTimestamp(9, row["createdAt"] as Timestamp)
                        val updatedAt = row["updatedAt"] as? Timestamp
                        if (updatedAt != null) ps.setTimestamp(10, updatedAt) else ps.setNull(10, Types.TIMESTAMP)
                    }

                    override fun getBatchSize(): Int = chunk.size
                },
            )
        }

        log.info { "[device] 완료" }
    }
}
