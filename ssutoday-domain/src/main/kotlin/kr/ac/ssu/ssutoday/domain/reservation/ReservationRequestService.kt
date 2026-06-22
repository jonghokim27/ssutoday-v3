package kr.ac.ssu.ssutoday.domain.reservation

import kr.ac.ssu.ssutoday.core.exception.BusinessException
import kr.ac.ssu.ssutoday.core.status.SsuStatus
import kr.ac.ssu.ssutoday.domain.reservation.factory.toView
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
        repository.findById(requestId)
            .orElseThrow { BusinessException(SsuStatus.SSU4120) }
            .toView()

    fun getStatus(requestId: Long, studentId: Int): Int =
        repository.findByIdAndStudentId(requestId, studentId)?.status
            ?: throw BusinessException(SsuStatus.SSU4120)

    fun accept(requestId: Long) {
        repository.findById(requestId)
            .orElseThrow { BusinessException(SsuStatus.SSU4120) }
            .accept()
    }

    fun updateStatus(requestId: Long, status: ReservationRequestStatus) {
        repository.findById(requestId)
            .orElseThrow { BusinessException(SsuStatus.SSU4120) }
            .updateStatus(status)
    }
}
