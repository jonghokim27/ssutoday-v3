package kr.ac.ssu.ssutoday.domain.sso

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id

@Entity
class SsoClient(
    @Id
    @Column(length = 20)
    var id: String,
    @Column(nullable = false, length = 500)
    var secret: String,
    @Column(nullable = false, length = 200)
    var callbackUrl: String,
)
