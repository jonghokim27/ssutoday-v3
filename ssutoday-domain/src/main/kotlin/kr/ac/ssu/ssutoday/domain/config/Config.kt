package kr.ac.ssu.ssutoday.domain.config

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id

@Entity
class Config(
    @Id
    @Column(length = 100)
    var key: String,
    @Column(nullable = false, length = 100)
    var value: String,
)
