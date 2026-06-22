package kr.ac.ssu.ssutoday.domain.config

import org.springframework.data.jpa.repository.JpaRepository

interface ConfigRepository : JpaRepository<Config, String>
