package kr.ac.ssu.ssutoday.domain.student

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.sql.Timestamp

@Entity
@Table(uniqueConstraints = [UniqueConstraint(name = "device_attestation_key_id_uindex", columnNames = ["key_id"])])
class DeviceAttestation(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0L,
    @Column(nullable = false)
    val studentId: Int,
    @Column(nullable = false, length = 44)
    val keyId: String,
    @Column(nullable = false, length = 255)
    val publicKey: ByteArray,
    @Column(nullable = false)
    val production: Boolean,
    @Column(nullable = false, length = 32)
    val registrationHash: ByteArray,
    @Column(nullable = false)
    val counter: Long = 0,
    @Column(nullable = false)
    val createdAt: Timestamp = Timestamp(System.currentTimeMillis()),
)
