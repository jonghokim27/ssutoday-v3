package kr.ac.ssu.ssutoday.domain.student

import org.springframework.data.jpa.repository.JpaRepository

interface BiometricsKeyRepository : JpaRepository<BiometricsKey, Long> {
    fun findByStudentIdAndOsTypeAndUuid(studentId: Int, osType: String, uuid: String): BiometricsKey?
}
