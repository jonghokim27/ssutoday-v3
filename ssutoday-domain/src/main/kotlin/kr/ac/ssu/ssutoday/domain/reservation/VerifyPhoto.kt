package kr.ac.ssu.ssutoday.domain.reservation

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import java.sql.Timestamp

@Entity
class VerifyPhoto(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idx")
    var id: Long = 0L,

    @Column(name = "ReserveIdx", nullable = false)
    var reservationId: Long,

    @Column(nullable = false)
    var url: String,

    @Column(name = "createdAt", nullable = false)
    var createdAt: Timestamp = Timestamp(System.currentTimeMillis()),
)
