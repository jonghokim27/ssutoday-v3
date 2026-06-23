package kr.ac.ssu.ssutoday.domain.sso

import org.springframework.data.jpa.repository.JpaRepository

interface SsoClientRepository : JpaRepository<SsoClient, String> {
    fun findByIdAndSecret(
        id: String,
        secret: String,
    ): SsoClient?
}
