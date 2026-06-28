package kr.ac.ssu.ssutoday.domain.reservation

import kr.ac.ssu.ssutoday.core.exception.BusinessException
import kr.ac.ssu.ssutoday.core.status.StatusCode
import kr.ac.ssu.ssutoday.domain.reservation.factory.toView
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class ReservationService(
    private val repository: ReservationRepository,
    private val policy: ReservationPolicy,
) {
    fun validate(
        startBlock: Int,
        endBlock: Int,
        admin: Boolean = false,
    ) {
        policy.validateBlocks(startBlock, endBlock, admin)
    }

    fun hasRoomConflict(
        roomNo: String,
        date: LocalDate,
        startBlock: Int,
        endBlock: Int,
    ): Boolean = repository.existsRoomConflict(date, roomNo, startBlock, endBlock)

    fun hasStudentConflict(
        studentId: Int,
        date: LocalDate,
        startBlock: Int,
        endBlock: Int,
    ): Boolean = repository.existsStudentConflict(studentId, date, startBlock, endBlock)

    fun create(
        studentId: Int,
        roomNo: String,
        date: LocalDate,
        startBlock: Int,
        endBlock: Int,
    ): ReservationView =
        repository
            .save(
                Reservation(
                    studentId = studentId,
                    roomNo = roomNo,
                    date = date,
                    startBlock = startBlock,
                    endBlock = endBlock,
                ),
            ).toView()

    fun find(reservationId: Long): ReservationView? = repository.findByIdOrNull(reservationId)?.toView()

    fun findByAdminToken(adminToken: String): ReservationView? = repository.findByAdminToken(adminToken)?.toView()

    fun getActive(
        studentId: Int,
        reservationId: Long,
        status: StatusCode,
    ): ReservationView =
        repository
            .findByIdAndStudentIdAndDeletedAtIsNull(reservationId, studentId)
            ?.toView()
            ?: throw BusinessException(status)

    fun cancelByAdmin(
        reservationId: Long,
        reason: String,
    ) {
        val reservation =
            repository.findByIdOrNull(reservationId)
                ?: throw IllegalStateException("Reservation $reservationId disappeared")
        reservation.cancel(reason)
    }

    fun resetCreatedAt(reservationId: Long) {
        val reservation =
            repository.findByIdOrNull(reservationId)
                ?: throw IllegalStateException("Reservation $reservationId disappeared")
        reservation.resetCreatedAt()
    }

    fun finish(
        reservationId: Long,
        endBlock: Int,
    ) {
        val reservation =
            repository.findByIdOrNull(reservationId)
                ?: throw IllegalStateException("Reservation $reservationId disappeared")
        reservation.finishAt(endBlock)
    }

    fun cancel(
        studentId: Int,
        reservationId: Long,
        reason: String,
    ) {
        val reservation =
            repository.findByIdAndStudentIdAndDeletedAtIsNull(reservationId, studentId)
                ?: throw BusinessException(StatusCode.SSU4140)
        val now = LocalDateTime.now()
        val startAt = reservation.date.atStartOfDay().plusMinutes(reservation.startBlock * 30L)
        val endAt = reservation.date.atStartOfDay().plusMinutes((reservation.endBlock + 1) * 30L)

        if (reservation.date.isBefore(now.toLocalDate())) {
            throw BusinessException(StatusCode.SSU4141)
        }
        if (now.isAfter(endAt)) {
            throw BusinessException(StatusCode.SSU4142)
        }
        if (now.isAfter(startAt)) {
            throw BusinessException(StatusCode.SSU4143)
        }
        reservation.cancel(reason)
    }

    fun getForPhotoUpload(
        studentId: Int,
        reservationId: Long,
    ): ReservationView {
        val reservation =
            repository.findByIdAndStudentIdAndDeletedAtIsNull(reservationId, studentId)
                ?: throw BusinessException(StatusCode.SSU4200)
        val now = LocalDateTime.now()
        val startAt = reservation.date.atStartOfDay().plusMinutes(reservation.startBlock * 30L)
        val endAt = reservation.date.atStartOfDay().plusMinutes((reservation.endBlock + 1) * 30L)
        val createdAt = reservation.createdAt.toLocalDateTime()
        val useStartAt = maxOf(startAt, createdAt)

        if (reservation.date.isBefore(now.toLocalDate())) {
            throw BusinessException(StatusCode.SSU4201)
        }
        if (now.isAfter(endAt)) {
            throw BusinessException(StatusCode.SSU4202)
        }
        if (now.isBefore(startAt)) {
            throw BusinessException(StatusCode.SSU4203)
        }
        if (now.isAfter(useStartAt.plusMinutes(10))) {
            throw BusinessException(StatusCode.SSU4204)
        }
        return reservation.toView()
    }

    fun findPrevious(
        studentId: Int,
        date: LocalDate,
        block: Int,
        pageable: Pageable,
    ): Page<ReservationView> = repository.previous(studentId, date, block, pageable).map(Reservation::toView)

    fun findWaiting(
        studentId: Int,
        date: LocalDate,
        block: Int,
        pageable: Pageable,
    ): Page<ReservationView> = repository.waiting(studentId, date, block, pageable).map(Reservation::toView)

    fun findAll(
        roomNo: String,
        date: LocalDate,
    ): List<ReservationView> = repository.findAllByRoomNoAndDateAndDeletedAtIsNull(roomNo, date).map(Reservation::toView)

    fun isContinuous(reservation: ReservationView): Boolean =
        repository.countByStudentIdAndDateAndEndBlockAndRoomNoAndDeletedAtIsNull(
            studentId = reservation.studentId,
            date = reservation.date,
            endBlock = reservation.startBlock - 1,
            roomNo = reservation.roomNo,
        ) > 0

    fun totalReservedBlocks(
        studentId: Int,
        date: LocalDate,
    ): Int =
        repository
            .findAllByStudentIdAndDateAndDeletedAtIsNull(studentId, date)
            .sumOf { it.endBlock - it.startBlock + 1 }

    fun findMissingPhotoReservations(
        today: LocalDate,
        block: Int,
    ): List<ReservationView> = repository.findMissingPhotoReservations(today, block).map(Reservation::toView)

    fun findStartingSoon(
        date: LocalDate,
        startBlock: Int,
    ): List<ReservationView> = repository.findAllByDateAndStartBlockAndDeletedAtIsNull(date, startBlock).map(Reservation::toView)

    fun findEndingSoon(
        date: LocalDate,
        block: Int,
    ): List<ReservationView> = repository.findEndingSoon(date, block).map(Reservation::toView)

    fun findStartingNow(
        date: LocalDate,
        block: Int,
    ): List<ReservationView> = repository.findStartingNow(date, block).map(Reservation::toView)
}
