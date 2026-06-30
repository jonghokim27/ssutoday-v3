package kr.ac.ssu.ssutoday.migration.runner

import io.github.oshai.kotlinlogging.KotlinLogging
import kr.ac.ssu.ssutoday.migration.job.ArticleMigration
import kr.ac.ssu.ssutoday.migration.job.ConfigMigration
import kr.ac.ssu.ssutoday.migration.job.DeviceMigration
import kr.ac.ssu.ssutoday.migration.job.ReservationMigration
import kr.ac.ssu.ssutoday.migration.job.ReservationRequestMigration
import kr.ac.ssu.ssutoday.migration.job.RoomMigration
import kr.ac.ssu.ssutoday.migration.job.SsoClientMigration
import kr.ac.ssu.ssutoday.migration.job.StudentMigration
import kr.ac.ssu.ssutoday.migration.job.VerifyPhotoMigration
import kr.ac.ssu.ssutoday.migration.job.VersionMigration
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component

@Component
class MigrationRunner(
    private val studentMigration: StudentMigration,
    private val roomMigration: RoomMigration,
    private val ssoClientMigration: SsoClientMigration,
    private val configMigration: ConfigMigration,
    private val versionMigration: VersionMigration,
    private val articleMigration: ArticleMigration,
    private val deviceMigration: DeviceMigration,
    private val reservationMigration: ReservationMigration,
    private val reservationRequestMigration: ReservationRequestMigration,
    private val verifyPhotoMigration: VerifyPhotoMigration,
) : CommandLineRunner {
    private val log = KotlinLogging.logger {}

    override fun run(vararg args: String?) {
        log.info { "===== 마이그레이션 시작 =====" }

        studentMigration.migrate()
        roomMigration.migrate()
        ssoClientMigration.migrate()
        configMigration.migrate()
        versionMigration.migrate()
        articleMigration.migrate()
        deviceMigration.migrate()
        reservationMigration.migrate()
        reservationRequestMigration.migrate()
        verifyPhotoMigration.migrate()

        log.info { "===== 마이그레이션 완료 =====" }
    }
}
