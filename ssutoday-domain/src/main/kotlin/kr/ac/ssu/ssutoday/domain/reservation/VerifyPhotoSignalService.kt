package kr.ac.ssu.ssutoday.domain.reservation

import kr.ac.ssu.ssutoday.core.attestation.PlayIntegritySignals
import kr.ac.ssu.ssutoday.core.attestation.RiskFlag
import org.springframework.stereotype.Service
import java.sql.Timestamp
import java.time.Instant

@Service
class VerifyPhotoSignalService(
    private val repository: VerifyPhotoSignalRepository,
) {
    fun create(
        reservationId: Long,
        studentId: Int,
        receivedAt: Instant,
        turnstileSolvedAt: Instant,
        platform: String,
        verdict: String,
        photoUrl: String,
        signals: PlayIntegritySignals?,
        flags: Set<RiskFlag>,
    ) {
        repository.save(
            VerifyPhotoSignal(
                reservationId = reservationId,
                studentId = studentId,
                receivedAt = Timestamp.from(receivedAt),
                turnstileSolvedAt = Timestamp.from(turnstileSolvedAt),
                platform = platform,
                verdict = verdict,
                photoUrl = photoUrl,
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
