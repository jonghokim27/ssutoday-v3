package kr.ac.ssu.ssutoday.domain.sso

import org.springframework.data.repository.CrudRepository

interface SsoTokenRepository : CrudRepository<SsoToken, String>
