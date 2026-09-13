package kr.ac.ssu.ssutoday.domain.student

import kr.ac.ssu.ssutoday.domain.student.factory.DeviceAttestationFactory
import org.springframework.stereotype.Service

@Service
class DeviceAttestationService(
    private val repository: DeviceAttestationRepository,
) {
    fun find(keyId: String): DeviceAttestationView? = repository.findByKeyId(keyId)?.let(DeviceAttestationFactory::from)

    fun register(
        studentId: Int,
        keyId: String,
        publicKey: ByteArray,
        production: Boolean,
        registrationHash: ByteArray,
    ) {
        require(studentId > 0 && publicKey.isNotEmpty() && publicKey.size <= 255 && registrationHash.size == 32)
        repository.saveAndFlush(
            DeviceAttestation(
                studentId = studentId,
                keyId = keyId,
                publicKey = publicKey.copyOf(),
                production = production,
                registrationHash = registrationHash.copyOf(),
            ),
        )
    }

    fun advanceCounter(
        keyId: String,
        studentId: Int,
        production: Boolean,
        counter: Long,
    ): Boolean {
        require(counter in 1..0xffffffffL)
        return repository.advanceCounter(keyId, studentId, production, counter) == 1
    }
}
