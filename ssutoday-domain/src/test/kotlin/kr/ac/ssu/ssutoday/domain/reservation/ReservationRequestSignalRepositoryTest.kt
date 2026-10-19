package kr.ac.ssu.ssutoday.domain.reservation

import jakarta.persistence.EntityManager
import kr.ac.ssu.ssutoday.core.attestation.PlayIntegritySignals
import kr.ac.ssu.ssutoday.core.attestation.RiskFlag
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.springframework.core.io.ClassPathResource
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.SharedEntityManagerCreator
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.mysql.MySQLContainer
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReservationRequestSignalRepositoryTest {
    private lateinit var factory: LocalContainerEntityManagerFactoryBean
    private lateinit var entityManager: EntityManager
    private lateinit var repository: ReservationRequestSignalRepository
    private lateinit var service: ReservationRequestSignalService
    private lateinit var transaction: TransactionTemplate
    private val receivedAt = Instant.parse("2026-10-18T11:00:00.083Z")

    @BeforeAll
    fun setUp() {
        val source = DriverManagerDataSource(mysql.jdbcUrl, mysql.username, mysql.password)
        ResourceDatabasePopulator(ClassPathResource("20261019-reservation-request-signal.sql")).execute(source)
        factory =
            LocalContainerEntityManagerFactoryBean().apply {
                dataSource = source
                jpaVendorAdapter = HibernateJpaVendorAdapter()
                setManagedTypes(PersistenceManagedTypes.of(ReservationRequestSignal::class.java.name))
                setJpaPropertyMap(
                    mapOf(
                        "hibernate.hbm2ddl.auto" to "validate",
                        "hibernate.physical_naming_strategy" to "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy",
                    ),
                )
                afterPropertiesSet()
            }
        val emf = requireNotNull(factory.`object`)
        transaction = TransactionTemplate(JpaTransactionManager(emf))
        entityManager = SharedEntityManagerCreator.createSharedEntityManager(emf)
        repository = JpaRepositoryFactory(entityManager).getRepository(ReservationRequestSignalRepository::class.java)
        service = ReservationRequestSignalService(repository)
    }

    @AfterAll
    fun tearDown() {
        if (::factory.isInitialized) factory.destroy()
    }

    @BeforeEach
    fun clear() {
        transaction.executeWithoutResult { repository.deleteAllInBatch() }
    }

    @Test
    fun `실제 배포 DDL은 Hibernate와 일치하고 밀리초와 Android 신호를 보존한다`() {
        transaction.executeWithoutResult {
            service.create(
                requestId = 1,
                studentId = 20260000,
                receivedAt = receivedAt,
                openOffsetMillis = 83,
                turnstileSolvedAt = receivedAt.minusMillis(1_500),
                platform = "android",
                verdict = "VERIFIED",
                signals =
                    PlayIntegritySignals(
                        appsDetected = listOf("KNOWN_INSTALLED", "UNKNOWN_CONTROLLING"),
                        playProtectVerdict = "NO_ISSUES",
                        deviceActivityLevel = "LEVEL_4",
                        strongIntegrity = false,
                        appLicensingVerdict = "LICENSED",
                    ),
                flags = setOf(RiskFlag.DEVICE_ACTIVITY_HIGH, RiskFlag.OPEN_RUSH, RiskFlag.ACCESS_RISK),
            )
            service.create(2, 20260001, receivedAt, 5_000, receivedAt, "ios", "VERIFIED", null, emptySet())
        }
        transaction.executeWithoutResult {
            val android = repository.findById(1L).orElseThrow()
            assertEquals(receivedAt, android.receivedAt.toInstant())
            assertEquals(receivedAt.minusMillis(1_500), android.turnstileSolvedAt.toInstant())
            assertEquals("KNOWN_INSTALLED,UNKNOWN_CONTROLLING", android.appsDetected)
            assertEquals(false, android.strongIntegrity)
            assertEquals("OPEN_RUSH,ACCESS_RISK,DEVICE_ACTIVITY_HIGH", android.flags)

            val ios = repository.findById(2L).orElseThrow()
            assertNull(ios.appsDetected)
            assertNull(ios.strongIntegrity)
            assertEquals("", ios.flags)
        }
    }

    @Test
    fun `문서의 검토 쿼리로 신호가 여러 개 붙은 요청도 학생별로 집계한다`() {
        transaction.executeWithoutResult {
            listOf(
                Triple(1L, 20260000, setOf(RiskFlag.OPEN_RUSH)),
                Triple(2L, 20260000, setOf(RiskFlag.OPEN_RUSH, RiskFlag.ACCESS_RISK)),
                Triple(3L, 20260000, setOf(RiskFlag.ACCESS_RISK)),
                Triple(4L, 20260001, setOf(RiskFlag.OPEN_RUSH)),
            ).forEach { (id, student, flags) ->
                service.create(id, student, receivedAt, 100 * id, receivedAt, "android", "VERIFIED", null, flags)
            }
        }
        val rows =
            transaction.execute {
                entityManager
                    .createNativeQuery(
                        """
                        SELECT student_id, COUNT(*) FROM reservation_request_signal
                        WHERE received_at >= :since AND FIND_IN_SET('OPEN_RUSH', flags) > 0
                        GROUP BY student_id ORDER BY COUNT(*) DESC, student_id
                        """.trimIndent(),
                    ).setParameter("since", java.sql.Timestamp.from(receivedAt.minusSeconds(14 * 86_400)))
                    .resultList
                    .map { (it as Array<*>).let { row -> (row[0] as Number).toInt() to (row[1] as Number).toLong() } }
            }!!
        assertEquals(listOf(20260000 to 2L, 20260001 to 1L), rows)
    }

    companion object {
        @Container @JvmStatic
        val mysql = MySQLContainer("mysql:8.4")
    }
}
