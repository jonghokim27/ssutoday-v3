package kr.ac.ssu.ssutoday.domain.student

import org.springframework.data.repository.CrudRepository

interface RefreshTokenRepository : CrudRepository<RefreshToken, String>
