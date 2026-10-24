package kr.ac.ssu.ssutoday.domain.penalty

import kr.ac.ssu.ssutoday.core.penalty.PenaltyReason
import kr.ac.ssu.ssutoday.core.penalty.PenaltyType
import kr.ac.ssu.ssutoday.domain.reservation.Reservation
import kr.ac.ssu.ssutoday.domain.reservation.ReservationPolicy
import kr.ac.ssu.ssutoday.domain.reservation.ReservationRepository
import kr.ac.ssu.ssutoday.domain.reservation.ReservationService
import kr.ac.ssu.ssutoday.domain.reservation.VerifyPhoto
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.springframework.core.io.ByteArrayResource
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
import java.sql.SQLIntegrityConstraintViolationException
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 관리자 페널티 부여가 동시에 들어와도 같은 예약에 같은 종류는 한 번만 저장되는지 확인한다. */
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PenaltyGrantConcurrencyTest {
    private lateinit var factory: LocalContainerEntityManagerFactoryBean
    private lateinit var reservations: ReservationRepository
    private lateinit var penalties: PenaltyRepository
    private lateinit var reservationService: ReservationService
    private lateinit var penaltyService: PenaltyService
    private lateinit var transaction: TransactionTemplate
    private val now = LocalDateTime.of(2026, 11, 17, 15, 0)

    @BeforeAll
    fun setUp() {
        val source = DriverManagerDataSource(mysql.jdbcUrl, mysql.username, mysql.password)
        ResourceDatabasePopulator(
            ClassPathResource("20261025-penalty.sql"),
            ByteArrayResource(RESERVATION_DDL.toByteArray()),
        ).execute(source)
        factory =
            LocalContainerEntityManagerFactoryBean().apply {
                dataSource = source
                jpaVendorAdapter = HibernateJpaVendorAdapter()
                setManagedTypes(
                    PersistenceManagedTypes.of(Penalty::class.java.name, Reservation::class.java.name, VerifyPhoto::class.java.name),
                )
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
        val repositories = JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(emf))
        reservations = repositories.getRepository(ReservationRepository::class.java)
        penalties = repositories.getRepository(PenaltyRepository::class.java)
        reservationService = ReservationService(reservations, ReservationPolicy())
        penaltyService = PenaltyService(penalties, PenaltyPolicy("2026-11-01", "cse"))
    }

    @AfterAll
    fun tearDown() {
        if (::factory.isInitialized) factory.destroy()
    }

    @BeforeEach
    fun clear() {
        transaction.executeWithoutResult {
            penalties.deleteAllInBatch()
            reservations.deleteAllInBatch()
        }
    }

    @Test
    fun `예약 행을 잠그면 동시에 들어온 부여 요청 중 하나만 저장한다`() {
        val token = saveReservation()
        val pool = Executors.newFixedThreadPool(2)
        try {
            val results = List(2) { pool.submit<Boolean> { grantLikeAdmin(token) } }.map { it.get() }
            assertEquals(listOf(false, true), results.sorted())
        } finally {
            pool.shutdown()
        }
        assertEquals(1, transaction.execute { penalties.count() })
    }

    @Test
    fun `잠금 없이 들어와도 DB가 같은 예약의 같은 종류 유효 페널티를 하나만 허용한다`() {
        val reservationId = reservations.findByAdminToken(saveReservation())!!.id
        transaction.executeWithoutResult { penaltyService.create(20260000, reservationId, PenaltyReason.PHOTO_OUTSIDE, "관리자", now) }

        val failure =
            assertFailsWith<Exception> {
                transaction.executeWithoutResult { penaltyService.create(20260000, reservationId, PenaltyReason.PHOTO_MISSING, "시스템", now) }
            }
        assertTrue(generateSequence<Throwable>(failure) { it.cause }.any { it is SQLIntegrityConstraintViolationException })

        // 다른 종류이거나 기존 페널티를 취소한 뒤에는 다시 부여할 수 있다.
        transaction.executeWithoutResult { penaltyService.create(20260000, reservationId, PenaltyReason.FRAUD_PHOTO, "관리자", now) }
        transaction.executeWithoutResult {
            val first = penalties.findAll().first { it.reason == PenaltyReason.PHOTO_OUTSIDE.name }
            penaltyService.revoke(first.id, "관리자", "이의제기 인용", now)
        }
        transaction.executeWithoutResult { penaltyService.create(20260000, reservationId, PenaltyReason.PHOTO_OUTSIDE, "관리자", now) }
        assertEquals(3, transaction.execute { penalties.count() })
    }

    @Test
    fun `취소 요청은 페널티 행을 잠근 채 최신 상태를 읽는다`() {
        val reservationId = reservations.findByAdminToken(saveReservation())!!.id
        val token =
            transaction.execute {
                penaltyService
                    .create(
                        20260000,
                        reservationId,
                        PenaltyReason.PHOTO_OUTSIDE,
                        "관리자",
                        now,
                    ).adminToken
            }!!
        val pool = Executors.newFixedThreadPool(2)
        val revoked =
            try {
                List(2) {
                    pool.submit<Boolean> {
                        transaction.execute {
                            val penalty = penaltyService.findByAdminTokenForUpdate(token)!!
                            if (penalty.revokedAt != null) return@execute false
                            Thread.sleep(300)
                            penaltyService.revoke(penalty.id, "관리자", "사유", now)
                            true
                        }!!
                    }
                }.map { it.get() }
            } finally {
                pool.shutdown()
            }
        assertEquals(listOf(false, true), revoked.sorted())
    }

    /** PenaltyCommandApplicationService.grantByAdmin과 같은 순서로 확인하고 저장한다. */
    private fun grantLikeAdmin(token: String): Boolean =
        transaction.execute {
            val reservation = reservationService.findByAdminTokenForUpdate(token)!!
            if (penaltyService.existsActive(reservation.id, PenaltyType.PHOTO)) return@execute false
            Thread.sleep(300)
            penaltyService.create(reservation.studentId, reservation.id, PenaltyReason.PHOTO_OUTSIDE, "관리자", now)
            true
        }!!

    private fun saveReservation(): String =
        transaction.execute {
            reservations
                .save(Reservation(studentId = 20260000, roomNo = "1", date = LocalDate.of(2026, 11, 17), startBlock = 20, endBlock = 23))
                .adminToken
        }!!

    companion object {
        @Container @JvmStatic
        val mysql = MySQLContainer("mysql:8.4")

        // 예약·인증샷 테이블은 저장소에 DDL이 없어 엔티티와 같은 구조로 만든다. 예약 조회 쿼리가 인증샷을 참조한다.
        private val RESERVATION_DDL =
            """
            CREATE TABLE reservation (
                id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                student_id INT NOT NULL,
                room_no VARCHAR(10) NOT NULL,
                date DATE NOT NULL,
                start_block INT NOT NULL,
                end_block INT NOT NULL,
                created_at DATETIME(6) NOT NULL,
                deleted_at DATETIME(6) NULL,
                deleted_reason VARCHAR(255) NULL,
                admin_token VARCHAR(220) NOT NULL UNIQUE,
                photo_reject_count INT NOT NULL
            );
            CREATE TABLE verify_photo (
                id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                reservation_id BIGINT NOT NULL,
                url VARCHAR(255) NOT NULL,
                created_at DATETIME(6) NOT NULL,
                CONSTRAINT verify_photo_reservation_id_uindex UNIQUE (reservation_id)
            );
            """.trimIndent()
    }
}
