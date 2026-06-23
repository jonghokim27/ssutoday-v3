package kr.ac.ssu.ssutoday.domain.student

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id

@Entity
class Version(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idx")
    var id: Long = 0L,
    @Column(name = "osType", nullable = false, length = 10)
    var osType: String,
    @Column(name = "requiredVersion", nullable = false, length = 10)
    var requiredVersion: String,
)
