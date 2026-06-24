package kr.ac.ssu.ssutoday.application.reservation

import kr.ac.ssu.ssutoday.application.reservation.dto.ReservationDetail
import kr.ac.ssu.ssutoday.application.reservation.dto.ReservationPageResult
import kr.ac.ssu.ssutoday.application.reservation.dto.ReservationPhoto
import kr.ac.ssu.ssutoday.application.reservation.dto.ReservationRoom
import kr.ac.ssu.ssutoday.application.reservation.dto.RoomReservation
import kr.ac.ssu.ssutoday.domain.reservation.ReservationRequestService
import kr.ac.ssu.ssutoday.domain.reservation.ReservationService
import kr.ac.ssu.ssutoday.domain.reservation.VerifyPhotoService
import kr.ac.ssu.ssutoday.domain.room.RoomService
import kr.ac.ssu.ssutoday.domain.student.StudentService
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class ReservationQueryApplicationService(
    private val reservationService: ReservationService,
    private val reservationRequestService: ReservationRequestService,
    private val roomService: RoomService,
    private val verifyPhotoService: VerifyPhotoService,
    private val studentService: StudentService,
) {
    @Transactional(readOnly = true)
    fun listReservations(
        studentId: Int,
        page: Int,
        previous: Boolean,
    ): ReservationPageResult {
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val block = currentBlock(now)
        val result =
            if (previous) {
                reservationService.findPrevious(
                    studentId,
                    today,
                    block,
                    PageRequest.of(
                        page,
                        PAGE_SIZE,
                        Sort.by("date", "endBlock", "id").descending(),
                    ),
                )
            } else {
                reservationService.findWaiting(
                    studentId,
                    today,
                    block,
                    PageRequest.of(
                        page,
                        PAGE_SIZE,
                        Sort.by("date", "startBlock", "id").ascending(),
                    ),
                )
            }
        val reservations =
            result.content.map { reservation ->
                val room = roomService.get(reservation.roomNo, "", true)
                val photo = verifyPhotoService.find(reservation.id)
                ReservationDetail(
                    idx = reservation.id,
                    roomNo = reservation.roomNo,
                    date = reservation.date,
                    startBlock = reservation.startBlock,
                    endBlock = reservation.endBlock,
                    createdAt = reservation.createdAt,
                    deletedAt = reservation.deletedAt,
                    deletedReason = reservation.deletedReason,
                    roomByRoomNo =
                        ReservationRoom(
                            no = room.no,
                            name = room.name,
                            major = room.major,
                            capacity = room.capacity,
                            location = room.location,
                            tags = room.tags,
                            image = room.image,
                            bigImage = room.bigImage,
                            isAvailable = room.isAvailable,
                        ),
                    verifyPhotosByIdx =
                        listOfNotNull(
                            photo?.let {
                                ReservationPhoto(it.id, it.reservationId, it.url, it.createdAt)
                            },
                        ),
                    isContinuous = reservationService.isContinuous(reservation),
                )
            }
        return ReservationPageResult(reservations, result.totalPages, result.totalElements)
    }

    @Transactional(readOnly = true)
    fun getRoomReservations(
        roomNo: String,
        date: LocalDate,
        currentStudentId: Int,
        admin: Boolean,
    ): List<RoomReservation> =
        reservationService.findAll(roomNo, date).map { reservation ->
            val student = studentService.get(reservation.studentId)
            RoomReservation(
                idx = reservation.id.takeIf { admin },
                studentInfo =
                    if (admin) {
                        RoomReservation.StudentInfo(
                            studentId = student.id.toString(),
                            name = student.name,
                            major = student.major
                        )
                    } else {
                        RoomReservation.StudentInfo(
                            studentId = maskStudentId(student.id),
                            name = maskName(student.name),
                            major = student.major
                        )
                    },
                startBlock = reservation.startBlock,
                endBlock = reservation.endBlock,
                isMine = currentStudentId == reservation.studentId,
            )
        }

    @Transactional(readOnly = true)
    fun getReservationRequestStatus(
        requestId: Long,
        studentId: Int,
    ): Int = reservationRequestService.getStatus(requestId, studentId)

    private fun currentBlock(now: LocalDateTime): Int = (now.hour * 60 + now.minute) / 30

    private fun maskName(name: String): String {
        if (name.length <= 1) return name
        if (name.length == 2) return "${name.first()}*"
        return "${name.first()}${"*".repeat(name.length - 2)}${name.last()}"
    }

    private fun maskStudentId(studentId: Int): String {
        return "${studentId.toString().take(2)}****${studentId.toString().takeLast(2)}"
    }

    private companion object {
        const val PAGE_SIZE = 10
    }
}
