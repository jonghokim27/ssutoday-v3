package kr.ac.ssu.ssutoday.domain.student

import org.springframework.data.jpa.repository.JpaRepository

interface StudentRepository : JpaRepository<Student, Int> {
    fun findAllByXnApiTokenIsNotNull(): List<Student>
}
