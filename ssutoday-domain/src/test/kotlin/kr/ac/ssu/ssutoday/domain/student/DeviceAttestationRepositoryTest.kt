package kr.ac.ssu.ssutoday.domain.student

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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull

@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeviceAttestationRepositoryTest {
    private lateinit var factory: LocalContainerEntityManagerFactoryBean
    private lateinit var repository: DeviceAttestationRepository
    private lateinit var transaction: TransactionTemplate

    @BeforeAll
    fun setUp() {
        val source = DriverManagerDataSource(mysql.jdbcUrl, mysql.username, mysql.password)
        ResourceDatabasePopulator(ClassPathResource("20260913-device-attestation.sql")).execute(source)
        factory =
            LocalContainerEntityManagerFactoryBean().apply {
                dataSource = source
                jpaVendorAdapter = HibernateJpaVendorAdapter()
                setManagedTypes(PersistenceManagedTypes.of(DeviceAttestation::class.java.name))
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
            JpaRepositoryFactory(
                SharedEntityManagerCreator.createSharedEntityManager(emf),
            ).getRepository(DeviceAttestationRepository::class.java)
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
    fun `실제 배포 DDL은 Hibernate와 일치하며 keyId는 대소문자를 구분하고 소유자를 유일하게 둔다`() {
        val first = "A".repeat(43) + "="
        val second = "a" + "A".repeat(42) + "="
        transaction.executeWithoutResult {
            repository.saveAndFlush(entity(first))
            repository.saveAndFlush(entity(second))
        }
        assertFails { transaction.executeWithoutResult { repository.saveAndFlush(entity(first, 20260001)) } }
        transaction.executeWithoutResult {
            assertEquals(2, repository.count())
            assertEquals(20260000, repository.findByKeyId(first)!!.studentId)
            assertNotNull(repository.findByKeyId(second))
        }
    }

    @Test
    fun `동시에 같은 counter를 갱신해도 정확히 한 요청만 성공한다`() {
        val id = "A".repeat(43) + "="
        transaction.executeWithoutResult { repository.saveAndFlush(entity(id)) }
        val start = CountDownLatch(1)
        Executors.newFixedThreadPool(8).use { executor ->
            val requests =
                (1..8).map {
                    executor.submit<Int> {
                        start.await()
                        transaction.execute { repository.advanceCounter(id, 20260000, true, 7) }!!
                    }
                }
            start.countDown()
            assertEquals(1, requests.sumOf { it.get(15, TimeUnit.SECONDS) })
        }
        transaction.executeWithoutResult {
            assertEquals(0, repository.advanceCounter(id, 20260001, true, 8))
            assertEquals(0, repository.advanceCounter(id, 20260000, false, 8))
            assertEquals(0, repository.advanceCounter(id, 20260000, true, 6))
            assertEquals(1, repository.advanceCounter(id, 20260000, true, 0xffffffffL))
        }
        transaction.executeWithoutResult { assertEquals(0xffffffffL, repository.findByKeyId(id)!!.counter) }
    }

    private fun entity(
        id: String,
        owner: Int = 20260000,
    ) = DeviceAttestation(
        studentId = owner,
        keyId = id,
        publicKey = byteArrayOf(1, 2, 3),
        production = true,
        registrationHash = ByteArray(32),
    )

    companion object {
        @Container @JvmStatic
        val mysql = MySQLContainer("mysql:8.4")
    }
}
