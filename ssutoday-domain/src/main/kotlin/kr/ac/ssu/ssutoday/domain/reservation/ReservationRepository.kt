package kr.ac.ssu.ssutoday.domain.reservation

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.sql.Date

interface ReservationRepository : JpaRepository<Reservation, Long> {
    @Query(
        """
        select (count(r) > 0) from Reservation r
        where r.date = :date and r.roomNo = :roomNo and r.deletedAt is null
          and r.startBlock <= :endBlock and r.endBlock >= :startBlock
        """,
    )
    fun existsRoomConflict(date: Date, roomNo: String, startBlock: Int, endBlock: Int): Boolean

    @Query(
        """
        select (count(r) > 0) from Reservation r
        where r.studentId = :studentId and r.date = :date and r.deletedAt is null
          and r.startBlock <= :endBlock and r.endBlock >= :startBlock
        """,
    )
    fun existsStudentConflict(studentId: Int, date: Date, startBlock: Int, endBlock: Int): Boolean

    fun findAllByStudentIdAndDateAndDeletedAtIsNull(studentId: Int, date: Date): List<Reservation>
    fun findAllByRoomNoAndDateAndDeletedAtIsNull(roomNo: String, date: Date): List<Reservation>
    fun findByIdAndStudentIdAndDeletedAtIsNull(id: Long, studentId: Int): Reservation?
    fun countByStudentIdAndDateAndEndBlockAndRoomNoAndDeletedAtIsNull(
        studentId: Int,
        date: Date,
        endBlock: Int,
        roomNo: String,
    ): Long

    @Query(
        """
        select r from Reservation r
        where r.studentId = :studentId
          and (r.date < :date or (r.date = :date and r.endBlock < :block))
        """,
    )
    fun previous(studentId: Int, date: Date, block: Int, pageable: Pageable): Page<Reservation>

    @Query(
        """
        select r from Reservation r
        where r.studentId = :studentId
          and (r.date > :date or (r.date = :date and r.endBlock >= :block))
        """,
    )
    fun waiting(studentId: Int, date: Date, block: Int, pageable: Pageable): Page<Reservation>
}
