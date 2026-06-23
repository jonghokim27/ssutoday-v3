package kr.ac.ssu.ssutoday.api.room

import jakarta.validation.Valid
import kr.ac.ssu.ssutoday.api.config.LoginStudent
import kr.ac.ssu.ssutoday.api.common.SsuResponse
import kr.ac.ssu.ssutoday.api.room.dto.RoomGetRequest
import kr.ac.ssu.ssutoday.api.room.dto.RoomListRequest
import kr.ac.ssu.ssutoday.api.room.dto.RoomListResponse
import kr.ac.ssu.ssutoday.api.room.dto.RoomResponse
import kr.ac.ssu.ssutoday.api.room.mapper.toView
import kr.ac.ssu.ssutoday.application.reservation.ReservationQueryApplicationService
import kr.ac.ssu.ssutoday.application.room.RoomApplicationService
import kr.ac.ssu.ssutoday.core.status.SsuStatus
import kr.ac.ssu.ssutoday.domain.student.StudentView
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.sql.Date

@RestController
@RequestMapping("/room")
class RoomController(
    private val roomApplicationService: RoomApplicationService,
    private val reservationQueryApplicationService: ReservationQueryApplicationService,
) {
    @PostMapping("/get")
    @SsuResponse(SsuStatus.SSU2100)
    fun get(
        @LoginStudent student: StudentView,
        @Valid @RequestBody request: RoomGetRequest,
    ): RoomResponse {
        val room = roomApplicationService.get(request.roomNo, student.major, student.isAdmin)
        return RoomResponse(
            room.toView(
                reservationQueryApplicationService.room(
                    room.no,
                    Date.valueOf(request.date),
                    student.id,
                    student.isAdmin,
                ),
            ),
        )
    }

    @PostMapping("/list")
    @SsuResponse(SsuStatus.SSU2110)
    fun list(
        @LoginStudent student: StudentView,
        @Valid @RequestBody request: RoomListRequest,
    ): RoomListResponse {
        val date = Date.valueOf(request.date)
        val result = roomApplicationService.list(student.major, student.isAdmin)
            .map {
                it.toView(
                    reservationQueryApplicationService.room(it.no, date, student.id, student.isAdmin),
                )
            }
        return RoomListResponse(result)
    }
}
