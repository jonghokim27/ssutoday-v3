package kr.ac.ssu.ssutoday.migration.runner

import io.github.oshai.kotlinlogging.KotlinLogging
import kr.ac.ssu.ssutoday.migration.job.TableMigration
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component

@Component
class MigrationRunner(
    private val migrations: List<TableMigration>,
) : CommandLineRunner {
    private val log = KotlinLogging.logger {}

    override fun run(vararg args: String) {
        log.info { "===== 마이그레이션 시작 =====" }
        migrations.forEach { it.migrate() }
        log.info { "===== 마이그레이션 완료 =====" }
    }
}
