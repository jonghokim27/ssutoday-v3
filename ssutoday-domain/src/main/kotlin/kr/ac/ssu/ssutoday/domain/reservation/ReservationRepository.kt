package kr.ac.ssu.ssutoday.domain.reservation

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate

interface ReservationRepository : JpaRepository<Reservation, Long> {
    @Query(
        """
        select (count(r) > 0) from Reservation r
        where r.date = :date and r.roomNo = :roomNo and r.deletedAt is null
          and r.startBlock <= :endBlock and r.endBlock >= :startBlock
        """,
    )
    fun existsRoomConflict(
        date: LocalDate,
        roomNo: String,
        startBlock: Int,
        endBlock: Int,
    ): Boolean

    @Query(
        """
        select (count(r) > 0) from Reservation r
        where r.studentId = :studentId and r.date = :date and r.deletedAt is null
          and r.startBlock <= :endBlock and r.endBlock >= :startBlock
        """,
    )
    fun existsStudentConflict(
        studentId: Int,
        date: LocalDate,
        startBlock: Int,
        endBlock: Int,
    ): Boolean

    fun findAllByStudentIdAndDateAndDeletedAtIsNull(
        studentId: Int,
        date: LocalDate,
    ): List<Reservation>

    fun findAllByRoomNoAndDateAndDeletedAtIsNull(
        roomNo: String,
        date: LocalDate,
    ): List<Reservation>

    @Query(
        """
        select r from Reservation r
        where r.deletedAt is null
          and r.date = :today
          and r.startBlock <= :block
          and r.id not in (select vp.reservationId from VerifyPhoto vp)
          and not exists (
            select 1 from Reservation prev
            where prev.deletedAt is null
              and prev.studentId = r.studentId
              and prev.date = r.date
              and prev.roomNo = r.roomNo
              and prev.endBlock = r.startBlock - 1
          )
        """,
    )
    fun findMissingPhotoReservations(today: LocalDate, block: Int): List<Reservation>

    // 특정 startBlock에 시작하는 활성 예약 (이용 시작 5분 전 알림용)
    fun findAllByDateAndStartBlockAndDeletedAtIsNull(
        date: LocalDate,
        startBlock: Int,
    ): List<Reservation>

    // 특정 블록에 종료되며 연속 다음 예약이 없는 활성 예약 (이용 종료 5분 전 알림용)
    @Query(
        """
        select r from Reservation r
        where r.date = :date
          and r.endBlock = :block
          and r.deletedAt is null
          and not exists (
            select 1 from Reservation next
            where next.deletedAt is null
              and next.studentId = r.studentId
              and next.date = r.date
              and next.roomNo = r.roomNo
              and next.startBlock = r.endBlock + 1
          )
        """,
    )
    fun findEndingSoon(
        date: LocalDate,
        block: Int,
    ): List<Reservation>

    // 특정 블록에 시작하며 연속 이전 예약이 없는 활성 예약 (인증샷 촬영 알림용)
    @Query(
        """
        select r from Reservation r
        where r.date = :date
          and r.startBlock = :block
          and r.deletedAt is null
          and not exists (
            select 1 from Reservation prev
            where prev.deletedAt is null
              and prev.studentId = r.studentId
              and prev.date = r.date
              and prev.roomNo = r.roomNo
              and prev.endBlock = r.startBlock - 1
          )
        """,
    )
    fun findStartingNow(
        date: LocalDate,
        block: Int,
    ): List<Reservation>

    fun findByAdminToken(adminToken: String): Reservation?

    fun findByIdAndStudentIdAndDeletedAtIsNull(
        id: Long,
        studentId: Int,
    ): Reservation?

    fun countByStudentIdAndDateAndEndBlockAndRoomNoAndDeletedAtIsNull(
        studentId: Int,
        date: LocalDate,
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
    fun previous(
        studentId: Int,
        date: LocalDate,
        block: Int,
        pageable: Pageable,
    ): Page<Reservation>

    @Query(
        """
        select r from Reservation r
        where r.studentId = :studentId
          and (r.date > :date or (r.date = :date and r.endBlock >= :block))
        """,
    )
    fun waiting(
        studentId: Int,
        date: LocalDate,
        block: Int,
        pageable: Pageable,
    ): Page<Reservation>
}
