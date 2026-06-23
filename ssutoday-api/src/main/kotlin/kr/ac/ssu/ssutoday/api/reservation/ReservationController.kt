package kr.ac.ssu.ssutoday.api.reservation

import jakarta.validation.Valid
import kr.ac.ssu.ssutoday.api.config.LoginStudent
import kr.ac.ssu.ssutoday.api.common.ResponseStatus
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
import kr.ac.ssu.ssutoday.core.status.StatusCode
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
    @ResponseStatus(StatusCode.SSU2090)
    fun request(
        @LoginStudent student: StudentView,
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
    @ResponseStatus(StatusCode.SSU2120)
    fun status(
        @LoginStudent student: StudentView,
        @Valid @RequestBody request: ReservationIdRequest,
    ): ReservationStatusResponse {
        val status = reservationQueryApplicationService.getRequestStatus(request.idx, student.id)
        return ReservationStatusResponse(status)
    }

    @PostMapping("/list")
    @ResponseStatus(StatusCode.SSU2130)
    fun list(
        @LoginStudent student: StudentView,
        @Valid @RequestBody request: ReservationListRequest,
    ): ReservationListResponse {
        val result = reservationQueryApplicationService.list(student.id, request.page, request.type == 0)
        return ReservationListResponse(result.reservations, result.totalPages, result.totalElements)
    }

    @PostMapping("/cancel")
    @ResponseStatus(StatusCode.SSU2140)
    fun cancel(
        @LoginStudent student: StudentView,
        @Valid @RequestBody request: ReservationIdRequest,
    ) {
        reservationCommandApplicationService.cancel(student.id, request.idx)
    }

    @PostMapping("/verifyPhoto/upload")
    @ResponseStatus(StatusCode.SSU2200)
    fun upload(
        @LoginStudent student: StudentView,
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
    @ResponseStatus(StatusCode.SSU2220)
    fun adminTools(
        @LoginStudent student: StudentView,
        @Valid @RequestBody request: AdminReservationRequest,
    ): Int {
        if (!student.isAdmin) throw BusinessException(StatusCode.SSU4003)

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
    @ResponseStatus(StatusCode.SSU2230)
    fun done(
        @LoginStudent student: StudentView,
        @Valid @RequestBody request: ReservationIdRequest,
    ) {
        reservationCommandApplicationService.done(student.id, request.idx)
    }
}
