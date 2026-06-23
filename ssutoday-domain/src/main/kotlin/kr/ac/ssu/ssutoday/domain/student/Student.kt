package kr.ac.ssu.ssutoday.domain.student

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import java.sql.Timestamp

@Entity
class Student(
    @Id
    @Column(name = "id")
    var id: Int,
    @Column(nullable = false, length = 100)
    var name: String,
    @Column(nullable = false, length = 10)
    var major: String,
    @Column(name = "xnApiToken", length = 500)
    var xnApiToken: String? = null,
    @Column(name = "isAdmin", nullable = false)
    var adminValue: Int = 0,
    @Column(name = "createdAt", nullable = false)
    var createdAt: Timestamp = Timestamp(System.currentTimeMillis()),
    @Column(name = "updatedAt")
    var updatedAt: Timestamp? = null,
) {
    val isAdmin: Boolean get() = adminValue == 1

    fun updateProfile(
        name: String,
        major: String,
    ) {
        this.name = name
        this.major = major
        updatedAt = Timestamp(System.currentTimeMillis())
    }
}
