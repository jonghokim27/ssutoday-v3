package kr.ac.ssu.ssutoday.domain.reservation

import kr.ac.ssu.ssutoday.core.attestation.PlayIntegritySignals
import kr.ac.ssu.ssutoday.core.attestation.RiskFlag
import org.springframework.stereotype.Service
import java.sql.Timestamp
import java.time.Instant

@Service
class ReservationRequestSignalService(
    private val repository: ReservationRequestSignalRepository,
) {
    fun create(
        requestId: Long,
        studentId: Int,
        receivedAt: Instant,
        openOffsetMillis: Long,
        turnstileSolvedAt: Instant,
        platform: String,
        verdict: String,
        signals: PlayIntegritySignals?,
        flags: Set<RiskFlag>,
    ) {
        repository.save(
            ReservationRequestSignal(
                requestId = requestId,
                studentId = studentId,
                receivedAt = Timestamp.from(receivedAt),
                openOffsetMillis = openOffsetMillis,
                turnstileSolvedAt = Timestamp.from(turnstileSolvedAt),
                platform = platform,
                verdict = verdict,
                appsDetected = signals.toAppsDetectedColumn(),
                playProtectVerdict = signals?.playProtectVerdict,
                deviceActivityLevel = signals?.deviceActivityLevel,
                strongIntegrity = signals?.strongIntegrity,
                appLicensingVerdict = signals?.appLicensingVerdict,
                flags = flags.toFlagsColumn(),
            ),
        )
    }
}
