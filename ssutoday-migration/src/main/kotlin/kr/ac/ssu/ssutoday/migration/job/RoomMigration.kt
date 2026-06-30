package kr.ac.ssu.ssutoday.migration.job

import io.github.oshai.kotlinlogging.KotlinLogging
import kr.ac.ssu.ssutoday.migration.config.DB
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.BatchPreparedStatementSetter
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.PreparedStatement

@Component
class RoomMigration(
    @Qualifier(DB.OLD_JDBC) private val oldDb: JdbcTemplate,
    @Qualifier(DB.NEW_JDBC) private val newDb: JdbcTemplate,
    @Value("\${migration.batch-size:500}") private val batchSize: Int,
) : TableMigration {
    private val log = KotlinLogging.logger {}

    override fun migrate() {
        val rows = oldDb.queryForList(
            "SELECT no, name, major, capacity, location, tags, image, bigImage, isAvailable FROM `Room`",
        )
        log.info { "[room] ${rows.size}건 마이그레이션 시작" }

        rows.chunked(batchSize).forEach { chunk ->
            newDb.batchUpdate(
                "INSERT IGNORE INTO room (no, name, major, capacity, location, tags, image, big_image, is_available) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                object : BatchPreparedStatementSetter {
                    override fun setValues(
                        ps: PreparedStatement,
                        i: Int,
                    ) {
                        val row = chunk[i]
                        ps.setString(1, row["no"] as String)
                        ps.setString(2, row["name"] as String)
                        ps.setString(3, row["major"].toString())
                        ps.setInt(4, row["capacity"] as Int)
                        ps.setString(5, row["location"] as String)
                        ps.setString(6, row["tags"] as String)
                        ps.setString(7, row["image"] as String)
                        ps.setString(8, row["bigImage"] as String)
                        ps.setInt(9, row["isAvailable"] as Int)
                    }

                    override fun getBatchSize(): Int = chunk.size
                },
            )
        }

        log.info { "[room] 완료" }
    }
}
