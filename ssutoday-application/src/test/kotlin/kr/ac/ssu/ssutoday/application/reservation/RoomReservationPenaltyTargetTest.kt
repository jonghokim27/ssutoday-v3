package kr.ac.ssu.ssutoday.application.reservation

import kr.ac.ssu.ssutoday.domain.penalty.PenaltyPolicy
import kr.ac.ssu.ssutoday.domain.reservation.ReservationRequestService
import kr.ac.ssu.ssutoday.domain.reservation.ReservationService
import kr.ac.ssu.ssutoday.domain.reservation.ReservationView
import kr.ac.ssu.ssutoday.domain.reservation.VerifyPhotoService
import kr.ac.ssu.ssutoday.domain.room.RoomService
import kr.ac.ssu.ssutoday.domain.student.StudentService
import kr.ac.ssu.ssutoday.domain.student.StudentView
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.sql.Timestamp
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class RoomReservationPenaltyTargetTest {
    private val date = LocalDate.of(2026, 11, 17)
    private val reservations = mock(ReservationService::class.java)
    private val students = mock(StudentService::class.java)
    private val service =
        ReservationQueryApplicationService(
            reservations,
            mock(ReservationRequestService::class.java),
            mock(RoomService::class.java),
            mock(VerifyPhotoService::class.java),
            students,
            PenaltyPolicy("2026-11-01", "cse"),
        )

    init {
        `when`(reservations.findAll("1", date)).thenReturn(listOf(reservation(1, 20260000), reservation(2, 20269999)))
        `when`(students.get(20260000)).thenReturn(StudentView(20260000, "홍길동", "cse", false))
        `when`(students.get(20269999)).thenReturn(StudentView(20269999, "김미디어", "media", false))
    }

    @Test
    fun `관리자에게만 예약자가 페널티 대상인지 알려준다`() {
        assertEquals(listOf(true, false), service.getRoomReservations("1", date, 20200000, admin = true).map { it.penaltyTarget })
        assertEquals(listOf(null, null), service.getRoomReservations("1", date, 20200000, admin = false).map { it.penaltyTarget })
    }

    private fun reservation(
        id: Long,
        studentId: Int,
    ) = ReservationView(
        id = id,
        studentId = studentId,
        roomNo = "1",
        date = date,
        startBlock = 20,
        endBlock = 23,
        createdAt = Timestamp.valueOf(date.atStartOfDay()),
        deletedAt = null,
        deletedReason = null,
        active = true,
        adminToken = "token$id",
        photoDeleteCount = 0,
    )
}
