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
    @Column
    var id: Long = 0L,

    @Column(nullable = false, length = 10)
    var osType: String,

    @Column(nullable = false, length = 10)
    var requiredVersion: String,
)
