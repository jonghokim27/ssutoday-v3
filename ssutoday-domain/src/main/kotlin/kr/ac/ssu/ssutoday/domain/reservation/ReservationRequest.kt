package kr.ac.ssu.ssutoday.domain.reservation

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import java.sql.Timestamp
import java.time.LocalDate

@Entity
class ReservationRequest(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column
    var id: Long = 0L,

    @Column(nullable = false)
    var studentId: Int,

    @Column(nullable = false, length = 10)
    var roomNo: String,

    @Column(nullable = false)
    var date: LocalDate,

    @Column(nullable = false)
    var startBlock: Int,

    @Column(nullable = false)
    var endBlock: Int,

    @Column(nullable = false)
    var status: Int = ReservationRequestStatus.PENDING.code,

    @Column(nullable = false)
    var createdAt: Timestamp = Timestamp(System.currentTimeMillis()),

    @Column
    var updatedAt: Timestamp? = null,
) {
    fun accept() {
        updateStatus(ReservationRequestStatus.ACCEPTED)
    }

    fun updateStatus(status: ReservationRequestStatus) {
        this.status = status.code
        updatedAt = Timestamp(System.currentTimeMillis())
    }
}
