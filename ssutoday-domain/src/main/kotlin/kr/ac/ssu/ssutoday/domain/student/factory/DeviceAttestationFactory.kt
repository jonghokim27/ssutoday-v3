package kr.ac.ssu.ssutoday.domain.student.factory

import kr.ac.ssu.ssutoday.domain.student.DeviceAttestation
import kr.ac.ssu.ssutoday.domain.student.DeviceAttestationView

object DeviceAttestationFactory {
    fun from(entity: DeviceAttestation): DeviceAttestationView =
        DeviceAttestationView(
            entity.keyId,
            entity.studentId,
            entity.publicKey.copyOf(),
            entity.production,
            entity.registrationHash.copyOf(),
            entity.counter,
        )
}
