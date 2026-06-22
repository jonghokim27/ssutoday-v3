package kr.ac.ssu.ssutoday.api.reservation

import jakarta.validation.Valid
import kr.ac.ssu.ssutoday.api.config.AuthenticatedStudent
import kr.ac.ssu.ssutoday.api.common.SsuResponse
import kr.ac.ssu.ssutoday.api.reservation.dto.CreateReservationRequest
import kr.ac.ssu.ssutoday.api.reservation.dto.AdminReservationRequest
import kr.ac.ssu.ssutoday.api.reservation.dto.ReservationIdRequest
import kr.ac.ssu.ssutoday.api.reservation.dto.ReservationIdResponse
import kr.ac.ssu.ssutoday.api.reservation.dto.ReservationListRequest
import kr.ac.ssu.ssutoday.api.reservation.dto.ReservationListResponse
import kr.ac.ssu.ssutoday.api.reservation.dto.ReservationStatusResponse
import kr.ac.ssu.ssutoday.api.reservation.dto.VerifyPhotoRequest
import kr.ac.ssu.ssutoday.application.reservation.ReservationCommandApplicationService
import kr.ac.ssu.ssutoday.application.reservation.ReservationQueryApplicationService
import kr.ac.ssu.ssutoday.application.reservation.VerifyPhotoApplicationService
import kr.ac.ssu.ssutoday.application.reservation.dto.CreateReservationCommand
import kr.ac.ssu.ssutoday.application.reservation.dto.AdminReservationCommand
import kr.ac.ssu.ssutoday.core.exception.BusinessException
import kr.ac.ssu.ssutoday.application.reservation.dto.UploadPhotoCommand
import kr.ac.ssu.ssutoday.core.status.SsuStatus
import kr.ac.ssu.ssutoday.domain.student.StudentView
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.sql.Date

@RestController
@RequestMapping("/reserve")
class ReservationController(
    private val reservationCommandApplicationService: ReservationCommandApplicationService,
    private val reservationQueryApplicationService: ReservationQueryApplicationService,
    private val verifyPhotoApplicationService: VerifyPhotoApplicationService,
) {
    @PostMapping("/request")
    @SsuResponse(SsuStatus.SSU2090)
    fun request(
        @AuthenticatedStudent student: StudentView,
        @Valid @RequestBody request: CreateReservationRequest,
    ): ReservationIdResponse {
        val id = reservationCommandApplicationService.request(
            CreateReservationCommand(
                request.recaptchaToken,
                student.id,
                student.major,
                student.isAdmin,
                request.roomNo,
                Date.valueOf(request.date),
                request.startBlock,
                request.endBlock,
            ),
        )
        return ReservationIdResponse(id)
    }

    @PostMapping("/status")
    @SsuResponse(SsuStatus.SSU2120)
    fun status(
        @AuthenticatedStudent student: StudentView,
        @Valid @RequestBody request: ReservationIdRequest,
    ): ReservationStatusResponse {
        val status = reservationQueryApplicationService.getRequestStatus(request.idx, student.id)
        return ReservationStatusResponse(status)
    }

    @PostMapping("/list")
    @SsuResponse(SsuStatus.SSU2130)
    fun list(
        @AuthenticatedStudent student: StudentView,
        @Valid @RequestBody request: ReservationListRequest,
    ): ReservationListResponse {
        val result = reservationQueryApplicationService.list(student.id, request.page, request.type == 0)
        return ReservationListResponse(result.reservations, result.totalPages, result.totalElements)
    }

    @PostMapping("/cancel")
    @SsuResponse(SsuStatus.SSU2140)
    fun cancel(
        @AuthenticatedStudent student: StudentView,
        @Valid @RequestBody request: ReservationIdRequest,
    ) {
        reservationCommandApplicationService.cancel(student.id, request.idx)
    }

    @PostMapping("/verifyPhoto/upload")
    @SsuResponse(SsuStatus.SSU2200)
    fun upload(
        @AuthenticatedStudent student: StudentView,
        @Valid @ModelAttribute request: VerifyPhotoRequest,
    ) {
        val file = request.file
        verifyPhotoApplicationService.upload(
            UploadPhotoCommand(
                request.recaptchaToken,
                student.id,
                request.idx,
                file.contentType,
                file.size,
                file.inputStream,
            ),
        )
    }

    @PostMapping("/adminTools")
    @SsuResponse(SsuStatus.SSU2220)
    fun adminTools(
        @AuthenticatedStudent student: StudentView,
        @Valid @RequestBody request: AdminReservationRequest,
    ): Int {
        if (!student.isAdmin) throw BusinessException(SsuStatus.SSU4003)

        val status = reservationCommandApplicationService.admin(
            AdminReservationCommand(
                administratorId = student.id,
                type = request.type,
                osType = request.osType,
                uuid = request.uuid,
                signature = request.signature,
                reservationId = request.idx,
                text = request.text,
            ),
        )
        return status
    }

    @PostMapping("/done")
    @SsuResponse(SsuStatus.SSU2230)
    fun done(
        @AuthenticatedStudent student: StudentView,
        @Valid @RequestBody request: ReservationIdRequest,
    ) {
        reservationCommandApplicationService.done(student.id, request.idx)
    }
}
