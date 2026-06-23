package kr.ac.ssu.ssutoday.domain.student

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import java.sql.Timestamp

@Entity
class Device(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column
    var id: Long = 0L,
    @Column(nullable = false)
    var studentId: Int,
    @Column(nullable = false, length = 10)
    var osType: String,
    @Column(nullable = false, length = 200)
    var uuid: String,
    @Column(nullable = false, length = 200)
    var pushToken: String,
    @Column(nullable = false)
    var notice: Int = 1,
    @Column(nullable = false)
    var reserve: Int = 1,
    @Column(nullable = false)
    var lms: Int = 1,
    @Column(nullable = false)
    var createdAt: Timestamp = Timestamp(System.currentTimeMillis()),
    @Column
    var updatedAt: Timestamp? = null,
) {
    fun updatePushToken(pushToken: String) {
        this.pushToken = pushToken
        updatedAt = Timestamp(System.currentTimeMillis())
    }

    fun change(
        option: DeviceOption,
        enabled: Boolean,
    ) {
        val value = if (enabled) 1 else 0
        when (option) {
            DeviceOption.NOTICE -> notice = value
            DeviceOption.RESERVE -> reserve = value
            DeviceOption.LMS -> lms = value
        }
        updatedAt = Timestamp(System.currentTimeMillis())
    }
}
