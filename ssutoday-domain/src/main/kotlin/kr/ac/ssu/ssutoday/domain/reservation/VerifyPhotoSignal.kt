package kr.ac.ssu.ssutoday.domain.reservation

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import java.sql.Timestamp

/**
 * 인증샷 업로드별 검토 신호. 인증샷이 삭제되면 같은 예약에 다시 올릴 수 있으므로 업로드마다 남긴다.
 * 인증샷이 삭제돼도 유지하며 업로드 처리에는 관여하지 않는다.
 */
@Entity
class VerifyPhotoSignal(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column
    var id: Long = 0L,
    @Column(nullable = false)
    var reservationId: Long,
    @Column(nullable = false)
    var studentId: Int,
    @Column(nullable = false)
    var receivedAt: Timestamp,
    @Column(nullable = false)
    var turnstileSolvedAt: Timestamp,
    @Column(nullable = false, length = 16)
    var platform: String,
    @Column(nullable = false, length = 40)
    var verdict: String,
    @Column(nullable = false, length = 255)
    var photoUrl: String,
    @Column(length = 512)
    var appsDetected: String? = null,
    @Column(length = 32)
    var playProtectVerdict: String? = null,
    @Column(length = 32)
    var deviceActivityLevel: String? = null,
    @Column
    var strongIntegrity: Boolean? = null,
    @Column(length = 32)
    var appLicensingVerdict: String? = null,
    @Column(nullable = false, length = 255)
    var flags: String = "",
)
