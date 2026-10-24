package kr.ac.ssu.ssutoday.domain.penalty

import kr.ac.ssu.ssutoday.core.penalty.PenaltyReason
import kr.ac.ssu.ssutoday.core.penalty.PenaltyType
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
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PenaltyRepositoryTest {
    private lateinit var factory: LocalContainerEntityManagerFactoryBean
    private lateinit var repository: PenaltyRepository
    private lateinit var service: PenaltyService
    private lateinit var transaction: TransactionTemplate
    private val now = LocalDateTime.of(2026, 11, 17, 15, 12, 30, 123_000_000)

    @BeforeAll
    fun setUp() {
        val source = DriverManagerDataSource(mysql.jdbcUrl, mysql.username, mysql.password)
        ResourceDatabasePopulator(ClassPathResource("20261025-penalty.sql")).execute(source)
        factory =
            LocalContainerEntityManagerFactoryBean().apply {
                dataSource = source
                jpaVendorAdapter = HibernateJpaVendorAdapter()
                setManagedTypes(PersistenceManagedTypes.of(Penalty::class.java.name))
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
                .getRepository(PenaltyRepository::class.java)
        service = PenaltyService(repository, PenaltyPolicy("2026-11-01"))
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
    fun `실제 배포 DDL은 Hibernate와 일치하고 유효한 페널티만 최근 부여 순으로 조회한다`() {
        val revokeToken =
            transaction.execute {
                service.create(20260000, 1, PenaltyReason.PHOTO_MISSING, "시스템", now.minusDays(40))
                service.create(20260000, 2, PenaltyReason.PHOTO_OUTSIDE, "관리자", now.minusDays(3))
                service.create(20260000, 3, PenaltyReason.FRAUD_AUTOMATION, "관리자", now.minusDays(1))
                service.create(20260001, 4, PenaltyReason.PHOTO_REJECTED, "시스템", now)
                service.create(20260000, 5, PenaltyReason.PHOTO_MISSING, "시스템", now.minusDays(2)).adminToken
            }!!

        transaction.executeWithoutResult {
            val valid = service.findValid(20260000, now)
            assertEquals(listOf(3L, 5L, 2L), valid.map { it.reservationId })
            with(valid.first()) {
                assertEquals(PenaltyType.FRAUD, type)
                assertEquals(PenaltyReason.FRAUD_AUTOMATION, reason)
                assertEquals(now.minusDays(1), grantedAt)
                assertEquals(now.plusDays(89), expiresAt)
            }
            assertEquals(200, revokeToken.length)
        }

        transaction.executeWithoutResult {
            val penalty = assertNotNull(service.findByAdminToken(revokeToken))
            service.revoke(penalty.id, "관리자", "이의제기 인용", now)
        }

        transaction.executeWithoutResult {
            assertEquals(listOf(3L, 2L), service.findValid(20260000, now).map { it.reservationId })
            val revoked = assertNotNull(service.findByAdminToken(revokeToken))
            assertEquals(now, revoked.revokedAt)
            assertEquals("이의제기 인용", revoked.revokeReason)
        }
    }

    @Test
    fun `같은 예약의 같은 종류 페널티는 만료돼도 이미 부여된 것으로 본다`() {
        transaction.executeWithoutResult {
            service.create(20260000, 7, PenaltyReason.PHOTO_MISSING, "시스템", now.minusDays(60))
        }
        transaction.executeWithoutResult {
            assertTrue(service.existsActive(7, PenaltyType.PHOTO))
            assertFalse(service.existsActive(7, PenaltyType.FRAUD))
            assertFalse(service.existsActive(8, PenaltyType.PHOTO))
        }
    }

    companion object {
        @Container @JvmStatic
        val mysql = MySQLContainer("mysql:8.4")
    }
}
