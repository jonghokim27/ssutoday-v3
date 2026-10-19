package kr.ac.ssu.ssutoday.domain.reservation

import kr.ac.ssu.ssutoday.core.attestation.PlayIntegritySignals
import kr.ac.ssu.ssutoday.core.attestation.RiskFlag
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
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
class VerifyPhotoSignalRepositoryTest {
    private lateinit var factory: LocalContainerEntityManagerFactoryBean
    private lateinit var repository: VerifyPhotoSignalRepository
    private lateinit var service: VerifyPhotoSignalService
    private lateinit var transaction: TransactionTemplate
    private val receivedAt = Instant.parse("2026-10-18T11:12:34.567Z")

    @BeforeAll
    fun setUp() {
        val source = DriverManagerDataSource(mysql.jdbcUrl, mysql.username, mysql.password)
        ResourceDatabasePopulator(ClassPathResource("20261019-verify-photo-signal.sql")).execute(source)
        factory =
            LocalContainerEntityManagerFactoryBean().apply {
                dataSource = source
                jpaVendorAdapter = HibernateJpaVendorAdapter()
                setManagedTypes(PersistenceManagedTypes.of(VerifyPhotoSignal::class.java.name))
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
        repository =
            JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(emf))
                .getRepository(VerifyPhotoSignalRepository::class.java)
        service = VerifyPhotoSignalService(repository)
    }

    @AfterAll
    fun tearDown() {
        if (::factory.isInitialized) factory.destroy()
    }

    @Test
    fun `실제 배포 DDL은 Hibernate와 일치하고 같은 예약의 재업로드도 각각 남긴다`() {
        transaction.executeWithoutResult {
            service.create(
                reservationId = 42,
                studentId = 20260000,
                receivedAt = receivedAt,
                turnstileSolvedAt = receivedAt.minusMillis(2_000),
                platform = "android",
                verdict = "VERIFIED",
                photoUrl = "https://static.example.com/verifyPhoto/first.jpeg",
                signals =
                    PlayIntegritySignals(
                        appsDetected = listOf("UNKNOWN_CONTROLLING"),
                        playProtectVerdict = "HIGH_RISK",
                        strongIntegrity = true,
                        appLicensingVerdict = "LICENSED",
                    ),
                flags = setOf(RiskFlag.PLAY_PROTECT_RISK, RiskFlag.ACCESS_RISK),
            )
            service.create(
                42,
                20260000,
                receivedAt.plusSeconds(600),
                receivedAt.plusSeconds(598),
                "ios",
                "VERIFIED",
                "https://static.example.com/verifyPhoto/second.jpeg",
                null,
                emptySet(),
            )
        }
        transaction.executeWithoutResult {
            val (first, second) = repository.findAll().sortedBy { it.id }
            assertEquals(42L, first.reservationId)
            assertEquals(receivedAt, first.receivedAt.toInstant())
            assertEquals(receivedAt.minusMillis(2_000), first.turnstileSolvedAt.toInstant())
            assertEquals("https://static.example.com/verifyPhoto/first.jpeg", first.photoUrl)
            assertEquals("UNKNOWN_CONTROLLING", first.appsDetected)
            assertEquals(true, first.strongIntegrity)
            assertEquals("ACCESS_RISK,PLAY_PROTECT_RISK", first.flags)

            assertEquals(42L, second.reservationId)
            assertEquals("https://static.example.com/verifyPhoto/second.jpeg", second.photoUrl)
            assertNull(second.appsDetected)
            assertNull(second.strongIntegrity)
            assertEquals("", second.flags)
        }
    }

    companion object {
        @Container @JvmStatic
        val mysql = MySQLContainer("mysql:8.4")
    }
}
