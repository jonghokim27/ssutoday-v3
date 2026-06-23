package kr.ac.ssu.ssutoday.domain.student

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import java.sql.Timestamp

@Entity
class BiometricsKey(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idx")
    var id: Long = 0L,
    @Column(name = "StudentId", nullable = false)
    var studentId: Int,
    @Column(name = "osType", nullable = false, length = 10)
    var osType: String,
    @Column(nullable = false, length = 200)
    var uuid: String,
    @Column(name = "publicKey", nullable = false, length = 500)
    var publicKey: String,
    @Column(name = "createdAt", nullable = false)
    var createdAt: Timestamp = Timestamp(System.currentTimeMillis()),
)
