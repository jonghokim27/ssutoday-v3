package kr.ac.ssu.ssutoday.domain.reservation

import kr.ac.ssu.ssutoday.core.exception.BusinessException
import kr.ac.ssu.ssutoday.core.status.StatusCode
import kr.ac.ssu.ssutoday.domain.reservation.factory.toView
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.sql.Date
import java.time.LocalDateTime
import java.time.ZoneId

@Service
class ReservationService(
    private val repository: ReservationRepository,
    private val policy: ReservationPolicy,
) {
    fun validate(startBlock: Int, endBlock: Int) {
        policy.validateBlocks(startBlock, endBlock)
    }

    fun hasRoomConflict(roomNo: String, date: Date, startBlock: Int, endBlock: Int): Boolean =
        repository.existsRoomConflict(date, roomNo, startBlock, endBlock)

    fun hasStudentConflict(studentId: Int, date: Date, startBlock: Int, endBlock: Int): Boolean =
        repository.existsStudentConflict(studentId, date, startBlock, endBlock)

    fun create(
        studentId: Int,
        roomNo: String,
        date: Date,
        startBlock: Int,
        endBlock: Int,
    ): ReservationView = repository.save(
        Reservation(
            studentId = studentId,
            roomNo = roomNo,
            date = date,
            startBlock = startBlock,
            endBlock = endBlock,
        ),
    ).toView()

    fun find(reservationId: Long): ReservationView? =
        repository.findByIdOrNull(reservationId)?.toView()

    fun getActive(studentId: Int, reservationId: Long, status: StatusCode): ReservationView =
        repository.findByIdAndStudentIdAndDeletedAtIsNull(reservationId, studentId)
            ?.toView()
            ?: throw BusinessException(status)

    fun cancelByAdmin(reservationId: Long, reason: String) {
        val reservation = repository.findByIdOrNull(reservationId)
            ?: throw IllegalStateException("Reservation $reservationId disappeared")
        reservation.cancel(reason)
    }

    fun resetCreatedAt(reservationId: Long) {
        val reservation = repository.findByIdOrNull(reservationId)
            ?: throw IllegalStateException("Reservation $reservationId disappeared")
        reservation.resetCreatedAt()
    }

    fun finish(reservationId: Long, endBlock: Int) {
        val reservation = repository.findByIdOrNull(reservationId)
            ?: throw IllegalStateException("Reservation $reservationId disappeared")
        reservation.finishAt(endBlock)
    }

    fun cancel(studentId: Int, reservationId: Long, reason: String) {
        val reservation = repository.findByIdAndStudentIdAndDeletedAtIsNull(reservationId, studentId)
            ?: throw BusinessException(StatusCode.SSU4140)
        val now = LocalDateTime.now(SEOUL)
        val startAt = reservation.date.toLocalDate().atStartOfDay().plusMinutes(reservation.startBlock * 30L)
        val endAt = reservation.date.toLocalDate().atStartOfDay().plusMinutes((reservation.endBlock + 1) * 30L)

        if (reservation.date.toLocalDate().isBefore(now.toLocalDate())) {
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

    fun getForPhotoUpload(studentId: Int, reservationId: Long): ReservationView {
        val reservation = repository.findByIdAndStudentIdAndDeletedAtIsNull(reservationId, studentId)
            ?: throw BusinessException(StatusCode.SSU4200)
        val now = LocalDateTime.now(SEOUL)
        val startAt = reservation.date.toLocalDate().atStartOfDay().plusMinutes(reservation.startBlock * 30L)
        val endAt = reservation.date.toLocalDate().atStartOfDay().plusMinutes((reservation.endBlock + 1) * 30L)
        val createdAt = reservation.createdAt.toLocalDateTime()
        val useStartAt = maxOf(startAt, createdAt)

        if (reservation.date.toLocalDate().isBefore(now.toLocalDate())) {
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

    fun findPrevious(studentId: Int, date: Date, block: Int, pageable: Pageable): Page<ReservationView> =
        repository.previous(studentId, date, block, pageable).map(Reservation::toView)

    fun findWaiting(studentId: Int, date: Date, block: Int, pageable: Pageable): Page<ReservationView> =
        repository.waiting(studentId, date, block, pageable).map(Reservation::toView)

    fun findAll(roomNo: String, date: Date): List<ReservationView> =
        repository.findAllByRoomNoAndDateAndDeletedAtIsNull(roomNo, date).map(Reservation::toView)

    fun isContinuous(reservation: ReservationView): Boolean =
        repository.countByStudentIdAndDateAndEndBlockAndRoomNoAndDeletedAtIsNull(
            studentId = reservation.studentId,
            date = reservation.date,
            endBlock = reservation.startBlock - 1,
            roomNo = reservation.roomNo,
        ) > 0

    fun totalReservedBlocks(studentId: Int, date: Date): Int =
        repository.findAllByStudentIdAndDateAndDeletedAtIsNull(studentId, date)
            .sumOf { it.endBlock - it.startBlock + 1 }

    private companion object {
        val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
