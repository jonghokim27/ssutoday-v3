package kr.ac.ssu.ssutoday.domain.student

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface DeviceAttestationRepository : JpaRepository<DeviceAttestation, Long> {
    fun findByKeyId(keyId: String): DeviceAttestation?

    @Modifying(flushAutomatically = true)
    @Query(
        "update DeviceAttestation d set d.counter = :counter where d.keyId = :keyId and d.studentId = :studentId and d.production = :production and d.counter < :counter",
    )
    fun advanceCounter(
        keyId: String,
        studentId: Int,
        production: Boolean,
        counter: Long,
    ): Int
}
