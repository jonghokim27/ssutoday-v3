package kr.ac.ssu.ssutoday.application.room

import kr.ac.ssu.ssutoday.domain.room.RoomService
import kr.ac.ssu.ssutoday.domain.room.RoomView
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RoomApplicationService(
    private val roomService: RoomService,
) {
    @Transactional(readOnly = true)
    fun get(roomNo: String, major: String, admin: Boolean): RoomView =
        roomService.get(roomNo, major, admin)

    @Transactional(readOnly = true)
    fun list(major: String, admin: Boolean): List<RoomView> =
        roomService.findAll(major, admin)
}
