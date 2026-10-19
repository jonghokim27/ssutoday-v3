package kr.ac.ssu.ssutoday.domain.reservation

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import java.sql.Timestamp

/** 예약 요청별 검토 신호. 요청 처리에는 관여하지 않고 관리자 검토에만 쓴다. */
@Entity
class ReservationRequestSignal(
    @Id
    @Column
    var requestId: Long,
    @Column(nullable = false)
    var studentId: Int,
    @Column(nullable = false)
    var receivedAt: Timestamp,
    @Column(nullable = false)
    var openOffsetMillis: Long,
    @Column(nullable = false)
    var turnstileSolvedAt: Timestamp,
    @Column(nullable = false, length = 16)
    var platform: String,
    @Column(nullable = false, length = 40)
    var verdict: String,
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
