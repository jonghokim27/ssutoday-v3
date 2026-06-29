package kr.ac.ssu.ssutoday.domain.reservation

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import java.security.SecureRandom
import java.sql.Timestamp
import java.time.LocalDate
import java.util.Base64

@Entity
class Reservation(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column
    var id: Long = 0L,
    @Column(nullable = false)
    var studentId: Int,
    @Column(nullable = false, length = 10)
    var roomNo: String,
    @Column(nullable = false)
    var date: LocalDate,
    @Column(nullable = false)
    var startBlock: Int,
    @Column(nullable = false)
    var endBlock: Int,
    @Column(nullable = false)
    var createdAt: Timestamp = Timestamp(System.currentTimeMillis()),
    @Column
    var deletedAt: Timestamp? = null,
    @Column
    var deletedReason: String? = null,
    @Column(nullable = false, unique = true, length = 220)
    var adminToken: String = generateAdminToken(),
) {
    val active: Boolean get() = deletedAt == null

    fun cancel(reason: String) {
        deletedAt = Timestamp(System.currentTimeMillis())
        deletedReason = reason
    }

    fun resetCreatedAt() {
        createdAt = Timestamp(System.currentTimeMillis())
    }

    fun finishAt(endBlock: Int) {
        this.endBlock = endBlock
    }

    companion object {
        private val secureRandom = SecureRandom()

        fun generateAdminToken(): String {
            val bytes = ByteArray(150)
            secureRandom.nextBytes(bytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }
    }
}
