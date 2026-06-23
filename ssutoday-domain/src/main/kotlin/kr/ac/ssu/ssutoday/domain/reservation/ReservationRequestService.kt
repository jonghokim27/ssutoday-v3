package kr.ac.ssu.ssutoday.domain.reservation

import kr.ac.ssu.ssutoday.core.exception.BusinessException
import kr.ac.ssu.ssutoday.core.status.StatusCode
import kr.ac.ssu.ssutoday.domain.reservation.factory.toView
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.sql.Date

@Service
class ReservationRequestService(
    private val repository: ReservationRequestRepository,
) {
    fun create(
        studentId: Int,
        roomNo: String,
        date: Date,
        startBlock: Int,
        endBlock: Int,
    ): Long = repository.save(
        ReservationRequest(
            studentId = studentId,
            roomNo = roomNo,
            date = date,
            startBlock = startBlock,
            endBlock = endBlock,
        ),
    ).id

    fun get(requestId: Long): ReservationRequestView =
        (repository.findByIdOrNull(requestId)
            ?: throw BusinessException(StatusCode.SSU4120))
            .toView()

    fun getStatus(requestId: Long, studentId: Int): Int =
        repository.findByIdAndStudentId(requestId, studentId)?.status
            ?: throw BusinessException(StatusCode.SSU4120)

    fun accept(requestId: Long) {
        val request = repository.findByIdOrNull(requestId)
            ?: throw BusinessException(StatusCode.SSU4120)
        request.accept()
    }

    fun updateStatus(requestId: Long, status: ReservationRequestStatus) {
        val request = repository.findByIdOrNull(requestId)
            ?: throw BusinessException(StatusCode.SSU4120)
        request.updateStatus(status)
    }
}
