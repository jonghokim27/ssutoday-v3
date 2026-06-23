package kr.ac.ssu.ssutoday.domain.room

import kr.ac.ssu.ssutoday.core.exception.BusinessException
import kr.ac.ssu.ssutoday.core.status.StatusCode
import kr.ac.ssu.ssutoday.domain.room.factory.toView
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class RoomService(
    private val repository: RoomRepository,
) {
    fun get(
        roomNo: String,
        major: String,
        admin: Boolean,
    ): RoomView {
        val room =
            if (admin) {
                repository.findByIdOrNull(roomNo)
            } else {
                repository.findAccessible(roomNo, major)
            }
        return room?.toView() ?: throw BusinessException(StatusCode.SSU4000)
    }

    fun findAll(
        major: String,
        admin: Boolean,
    ): List<RoomView> = (if (admin) repository.findAll() else repository.findAllAccessible(major)).map(Room::toView)
}
