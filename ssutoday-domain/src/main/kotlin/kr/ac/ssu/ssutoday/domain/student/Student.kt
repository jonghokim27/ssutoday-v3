package kr.ac.ssu.ssutoday.domain.student

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import java.sql.Timestamp

@Entity
class Student(
    @Id
    @Column
    var id: Int,
    @Column(length = 100)
    var name: String,
    @Column(length = 10)
    var major: String,
    @Column(length = 500)
    var xnApiToken: String? = null,
    @Column(nullable = false)
    var isAdmin: Int = 0,
    @Column(nullable = false)
    var createdAt: Timestamp = Timestamp(System.currentTimeMillis()),
    @Column
    var updatedAt: Timestamp? = null,
) {
    fun updateProfile(
        name: String,
        major: String,
    ) {
        this.name = name
        this.major = major
        updatedAt = Timestamp(System.currentTimeMillis())
    }
}
