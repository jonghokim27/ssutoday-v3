package kr.ac.ssu.ssutoday.domain.student

import org.springframework.data.jpa.repository.JpaRepository

interface DeviceRepository : JpaRepository<Device, Long> {
    fun findByStudentIdAndOsTypeAndUuid(studentId: Int, osType: String, uuid: String): Device?
    fun findAllByStudentId(studentId: Int): List<Device>
}
