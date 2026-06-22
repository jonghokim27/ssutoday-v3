package kr.ac.ssu.ssutoday.domain.student

import org.springframework.data.jpa.repository.JpaRepository

interface VersionRepository : JpaRepository<Version, Long> {
    fun getByOsType(osType: String): Version
}
