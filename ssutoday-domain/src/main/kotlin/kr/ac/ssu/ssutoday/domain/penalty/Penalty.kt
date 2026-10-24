package kr.ac.ssu.ssutoday.domain.penalty

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import java.security.SecureRandom
import java.sql.Timestamp
import java.util.Base64

/** 예약 1건에 대해 부여한 페널티. 취소해도 지우지 않고 revokedAt을 남긴다. */
@Entity
class Penalty(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column
    var id: Long = 0L,
    @Column(nullable = false)
    var studentId: Int,
    @Column(nullable = false)
    var reservationId: Long,
    @Column(nullable = false, length = 16)
    var type: String,
    @Column(nullable = false, length = 32)
    var reason: String,
    @Column(nullable = false, length = 30)
    var grantedBy: String,
    @Column(nullable = false)
    var grantedAt: Timestamp,
    @Column(nullable = false)
    var expiresAt: Timestamp,
    /** Discord 페널티 부여 알림의 취소 링크에 쓰는 토큰. */
    @Column(nullable = false, unique = true, length = 220)
    var adminToken: String = generateAdminToken(),
    @Column
    var revokedAt: Timestamp? = null,
    @Column(length = 30)
    var revokedBy: String? = null,
    @Column(length = 200)
    var revokeReason: String? = null,
) {
    fun revoke(
        revokedBy: String,
        revokeReason: String,
        revokedAt: Timestamp,
    ) {
        this.revokedAt = revokedAt
        this.revokedBy = revokedBy
        this.revokeReason = revokeReason
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
