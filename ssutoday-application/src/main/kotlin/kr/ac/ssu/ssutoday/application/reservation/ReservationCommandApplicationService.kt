package kr.ac.ssu.ssutoday.application.reservation

import kr.ac.ssu.ssutoday.application.reservation.dto.AdminReservationCommand
import kr.ac.ssu.ssutoday.application.reservation.dto.CreateReservationCommand
import kr.ac.ssu.ssutoday.core.dto.PushMessage
import kr.ac.ssu.ssutoday.core.dto.PushMessages
import kr.ac.ssu.ssutoday.core.exception.BusinessException
import kr.ac.ssu.ssutoday.core.port.DiscordReservationActionNotificationPort
import kr.ac.ssu.ssutoday.core.port.PushMessagePublisher
import kr.ac.ssu.ssutoday.core.port.ReservationRequestPublisher
import kr.ac.ssu.ssutoday.core.port.TurnstileVerificationPort
import kr.ac.ssu.ssutoday.core.status.StatusCode
import kr.ac.ssu.ssutoday.core.transaction.afterCommit
import kr.ac.ssu.ssutoday.domain.config.ConfigService
import kr.ac.ssu.ssutoday.domain.reservation.ReservationCompletionPolicy
import kr.ac.ssu.ssutoday.domain.reservation.ReservationRequestPolicy
import kr.ac.ssu.ssutoday.domain.reservation.ReservationRequestService
import kr.ac.ssu.ssutoday.domain.reservation.ReservationService
import kr.ac.ssu.ssutoday.domain.reservation.ReservationView
import kr.ac.ssu.ssutoday.domain.reservation.VerifyPhotoService
import kr.ac.ssu.ssutoday.domain.room.RoomService
import kr.ac.ssu.ssutoday.domain.student.DeviceService
import kr.ac.ssu.ssutoday.domain.student.StudentService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Calendar
import java.util.TimeZone

@Service
class ReservationCommandApplicationService(
    private val reservationRequestService: ReservationRequestService,
    private val reservationService: ReservationService,
    private val verifyPhotoService: VerifyPhotoService,
    private val roomService: RoomService,
    private val studentService: StudentService,
    private val deviceService: DeviceService,
    private val configService: ConfigService,
    private val reservationRequestPolicy: ReservationRequestPolicy,
    private val reservationCompletionPolicy: ReservationCompletionPolicy,
    private val reservationRequestPublisher: ReservationRequestPublisher,
    private val pushMessagePublisher: PushMessagePublisher,
    private val turnstileVerificationPort: TurnstileVerificationPort,
    private val discordReservationActionNotificationPort: DiscordReservationActionNotificationPort,
) {
    @Transactional
    fun createReservationRequest(command: CreateReservationCommand): Long {
        if (!turnstileVerificationPort.verify(command.turnstileToken)) {
            throw BusinessException(StatusCode.SSU4092)
        }
        reservationService.validate(command.startBlock, command.endBlock, command.admin)
        val room = roomService.get(command.roomNo, command.major, command.admin)
        if (!room.isAvailable) {
            throw BusinessException(StatusCode.SSU4091)
        }
        if (configService.isReservationRequestDisabled()) {
            throw BusinessException(StatusCode.SSU4091)
        }
        val requestId =
            reservationRequestService.create(
                studentId = command.studentId,
                roomNo = command.roomNo,
                date = command.date,
                startBlock = command.startBlock,
                endBlock = command.endBlock,
            )
        afterCommit {
            reservationRequestPublisher.publish(requestId)
        }
        return requestId
    }

    @Transactional
    fun cancel(
        studentId: Int,
        reservationId: Long,
        reason: String = "사용자 취소",
    ) = reservationService.cancel(studentId, reservationId, reason)

    @Transactional
    fun processReservationRequest(requestId: Long) {
        val request = reservationRequestService.get(requestId)
        val student = studentService.get(request.studentId)
        val rejectionStatus =
            reservationRequestPolicy.rejectionStatus(
                request = request,
                admin = student.isAdmin,
                roomConflict =
                    reservationService.hasRoomConflict(
                        request.roomNo,
                        request.date,
                        request.startBlock,
                        request.endBlock,
                    ),
                reservedBlocks = reservationService.totalReservedBlocks(request.studentId, request.date),
                studentConflict =
                    reservationService.hasStudentConflict(
                        request.studentId,
                        request.date,
                        request.startBlock,
                        request.endBlock,
                    ),
                now = LocalDateTime.now(),
            )
        if (rejectionStatus != null) {
            reservationRequestService.updateStatus(requestId, rejectionStatus)
            return
        }

        val reservation =
            reservationService.create(
                studentId = request.studentId,
                roomNo = request.roomNo,
                date = request.date,
                startBlock = request.startBlock,
                endBlock = request.endBlock,
            )
        reservationRequestService.accept(requestId)

        val startAt = request.date.atStartOfDay().plusMinutes(request.startBlock * 30L)
        val isPassingTime = LocalDateTime.now().isAfter(startAt)
        val isContinuous = reservationService.isContinuous(reservation)
        val roomName = roomService.getByNo(request.roomNo)?.name ?: request.roomNo
        val studentId = request.studentId

        afterCommit {
            sendReservationPush(
                studentId = studentId,
                title = PushMessages.reserveConfirmTitle(roomName),
                body = PushMessages.reserveConfirmBody(request.date, request.startBlock, request.endBlock),
            )
            if (isPassingTime && !isContinuous) {
                sendReservationPush(
                    studentId = studentId,
                    title = PushMessages.VERIFY_PHOTO_TITLE,
                    body = PushMessages.VERIFY_PHOTO_BODY,
                )
            }
        }
    }

    @Transactional
    fun completeReservation(
        studentId: Int,
        reservationId: Long,
    ) {
        val reservation = reservationService.getActive(studentId, reservationId, StatusCode.SSU4230)
        val verifyPhotoSatisfied =
            reservationService.isContinuous(reservation) || verifyPhotoService.find(reservationId) != null
        val completionBlock =
            reservationCompletionPolicy.completionBlock(
                reservation,
                verifyPhotoSatisfied,
                LocalDateTime.now(),
            )
        reservationService.finish(reservationId, completionBlock)
    }

    @Transactional
    fun executeAdminAction(command: AdminReservationCommand): Int {
        val reservation = reservationService.find(command.reservationId) ?: return 0
        if (!reservation.active) return 1

        val endAt =
            reservation.date
                .atStartOfDay()
                .plusMinutes((reservation.endBlock + 1) * 30L)
        if (endAt.isBefore(LocalDateTime.now())) return 2

        return when (command.type) {
            ADMIN_CANCEL -> {
                val reason = command.text?.takeIf(String::isNotEmpty) ?: return 99
                reservationService.cancelByAdmin(
                    command.reservationId,
                    "관리자 취소 (사유: $reason)",
                )
                val studentId = reservation.studentId
                val roomName = roomService.getByNo(reservation.roomNo)?.name ?: reservation.roomNo
                afterCommit {
                    sendReservationPush(
                        studentId = studentId,
                        title = PushMessages.adminCancelTitle(roomName),
                        body = PushMessages.adminCancelBody(reason),
                    )
                    sendReservationDiscordNotice(
                        content = "**[예약 취소 알림]**",
                        reservation = reservation,
                        actionFieldName = "취소 구분",
                        actionFieldValue = "관리자 취소 (사유: $reason)",
                    )
                }
                3
            }
            PHOTO_DELETE -> {
                val photoUrl = verifyPhotoService.find(command.reservationId)?.url
                if (!verifyPhotoService.delete(command.reservationId)) return 4
                reservationService.resetCreatedAt(command.reservationId)
                val studentId = reservation.studentId
                afterCommit {
                    sendReservationPush(
                        studentId = studentId,
                        title = PushMessages.PHOTO_DELETE_TITLE,
                        body = PushMessages.PHOTO_DELETE_BODY,
                    )
                    sendReservationDiscordNotice(
                        content = "**[인증샷 삭제 알림]**",
                        reservation = reservation,
                        actionFieldName = "처리자",
                        actionFieldValue = "관리자",
                        photoUrl = photoUrl,
                    )
                }
                5
            }
            PHOTO_EXCEPT -> {
                if (!verifyPhotoService.createException(command.reservationId)) return 6
                val photoUrl = verifyPhotoService.find(command.reservationId)?.url
                val studentId = reservation.studentId
                afterCommit {
                    sendReservationPush(
                        studentId = studentId,
                        title = PushMessages.PHOTO_EXCEPT_TITLE,
                        body = PushMessages.PHOTO_EXCEPT_BODY,
                    )
                    sendReservationDiscordNotice(
                        content = "**[인증샷 촬영 예외 알림]**",
                        reservation = reservation,
                        actionFieldName = "처리자",
                        actionFieldValue = "관리자",
                        photoUrl = photoUrl,
                    )
                }
                7
            }
            else -> 99
        }
    }

    @Transactional
    fun executeAdminActionByToken(
        adminToken: String,
        action: String,
    ): Int {
        val reservation = reservationService.findByAdminToken(adminToken) ?: return 0
        if (!reservation.active) return 1

        val endAt = reservation.date
            .atStartOfDay()
            .plusMinutes((reservation.endBlock + 1) * 30L)
        if (endAt.isBefore(LocalDateTime.now())) return 2

        return when (action) {
            ADMIN_CANCEL -> {
                reservationService.cancelByAdmin(reservation.id, "관리자 취소 (Discord)")
                val studentId = reservation.studentId
                val roomName = roomService.getByNo(reservation.roomNo)?.name ?: reservation.roomNo
                afterCommit {
                    sendReservationPush(
                        studentId = studentId,
                        title = PushMessages.adminCancelTitle(roomName),
                        body = PushMessages.adminCancelBody("Discord"),
                    )
                    sendReservationDiscordNotice(
                        content = "**[예약 취소 알림]**",
                        reservation = reservation,
                        actionFieldName = "취소 구분",
                        actionFieldValue = "관리자 취소 (Discord)",
                    )
                }
                3
            }
            PHOTO_DELETE -> {
                val photoUrl = verifyPhotoService.find(reservation.id)?.url
                if (!verifyPhotoService.delete(reservation.id)) return 4
                reservationService.resetCreatedAt(reservation.id)
                val studentId = reservation.studentId
                afterCommit {
                    sendReservationPush(
                        studentId = studentId,
                        title = PushMessages.PHOTO_DELETE_TITLE,
                        body = PushMessages.PHOTO_DELETE_BODY,
                    )
                    sendReservationDiscordNotice(
                        content = "**[인증샷 삭제 알림]**",
                        reservation = reservation,
                        actionFieldName = "처리자",
                        actionFieldValue = "Discord",
                        photoUrl = photoUrl,
                    )
                }
                5
            }
            else -> 99
        }
    }

    @Transactional
    fun cancelMissingPhotos() {
        val now = LocalDateTime.now()
        val block = (now.hour * 60 + now.minute) / 30
        val candidates = reservationService.findMissingPhotoReservations(now.toLocalDate(), block)
        candidates.forEach { reservation ->
            val startAt = reservation.date.atStartOfDay().plusMinutes(reservation.startBlock * 30L)
            val useStartAt = maxOf(startAt, reservation.createdAt.toLocalDateTime())
            if (now.isAfter(useStartAt.plusMinutes(10))) {
                reservationService.cancelByAdmin(reservation.id, "인증샷 미촬영 취소")
                val studentId = reservation.studentId
                val roomName = roomService.getByNo(reservation.roomNo)?.name ?: reservation.roomNo
                afterCommit {
                    sendReservationPush(
                        studentId = studentId,
                        title = PushMessages.autoCancelTitle(roomName),
                        body = PushMessages.AUTO_CANCEL_BODY,
                    )
                    sendReservationDiscordNotice(
                        content = "**[예약 취소 알림]**",
                        reservation = reservation,
                        actionFieldName = "취소 구분",
                        actionFieldValue = "자동 취소 (사유: 인증샷을 촬영하지 않음)",
                    )
                }
            }
        }
    }

    @Transactional(readOnly = true)
    fun sendStartSoonNotifications() {
        val now = LocalDateTime.now()
        val nextBlock = (now.hour * 60 + now.minute) / 30 + 1
        reservationService.findStartingSoon(now.toLocalDate(), nextBlock).forEach { reservation ->
            val roomName = roomService.getByNo(reservation.roomNo)?.name ?: reservation.roomNo
            val studentId = reservation.studentId
            sendReservationPush(
                studentId = studentId,
                title = PushMessages.reserveStartSoonTitle(roomName),
                body = PushMessages.RESERVE_START_SOON_BODY,
            )
        }
    }

    @Transactional(readOnly = true)
    fun sendEndSoonNotifications() {
        val now = LocalDateTime.now()
        val currentBlock = (now.hour * 60 + now.minute) / 30
        reservationService.findEndingSoon(now.toLocalDate(), currentBlock).forEach { reservation ->
            val roomName = roomService.getByNo(reservation.roomNo)?.name ?: reservation.roomNo
            val studentId = reservation.studentId
            sendReservationPush(
                studentId = studentId,
                title = PushMessages.reserveEndSoonTitle(roomName),
                body = PushMessages.RESERVE_END_SOON_BODY,
            )
        }
    }

    @Transactional(readOnly = true)
    fun sendVerifyPhotoNotifications() {
        val now = LocalDateTime.now()
        val currentBlock = (now.hour * 60 + now.minute) / 30
        reservationService.findStartingNow(now.toLocalDate(), currentBlock).forEach { reservation ->
            val studentId = reservation.studentId
            sendReservationPush(
                studentId = studentId,
                title = PushMessages.VERIFY_PHOTO_TITLE,
                body = PushMessages.VERIFY_PHOTO_BODY,
            )
        }
    }

    private fun sendReservationDiscordNotice(
        content: String,
        reservation: ReservationView,
        actionFieldName: String,
        actionFieldValue: String,
        photoUrl: String? = null,
    ) {
        val student = studentService.get(reservation.studentId)
        val roomName = roomService.getByNo(reservation.roomNo)?.name ?: reservation.roomNo
        discordReservationActionNotificationPort.send(
            content = content,
            reservationId = reservation.id,
            studentInfo = buildStudentInfo(student.name, student.id, student.major),
            roomName = roomName,
            reservationDateTime = buildReservationDateTime(reservation.date, reservation.startBlock, reservation.endBlock),
            actionFieldName = actionFieldName,
            actionFieldValue = actionFieldValue,
            photoUrl = photoUrl,
        )
    }

    private fun sendReservationPush(
        studentId: Int,
        title: String,
        body: String,
    ) {
        deviceService.findReservationPushTokens(studentId).forEach { token ->
            pushMessagePublisher.publish(
                PushMessage(
                    type = "token",
                    token = token,
                    title = title,
                    body = body,
                    link = "ssutoday://reservations",
                ),
            )
        }
    }

    private fun buildStudentInfo(
        name: String,
        studentId: Int,
        major: String,
    ): String = "$name ($studentId/${majorShortName(major)})"

    private fun buildReservationDateTime(
        date: LocalDate,
        startBlock: Int,
        endBlock: Int,
    ): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul")).apply {
            set(date.year, date.monthValue - 1, date.dayOfMonth, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val dateText = SimpleDateFormat("yyyy년 MM월 dd일").apply {
            timeZone = TimeZone.getTimeZone("Asia/Seoul")
        }.format(calendar.time)
        val day = DAYS[calendar.get(Calendar.DAY_OF_WEEK) - 1]

        return "$dateText($day) ${blockToTime(startBlock)} ~ ${blockToTime(endBlock + 1)}"
    }

    private fun blockToTime(block: Int): String {
        val totalMinutes = block * 30
        return "%02d:%02d".format(totalMinutes / 60, totalMinutes % 60)
    }

    private fun majorShortName(major: String): String =
        when (major) {
            "cse" -> "컴"
            "sw" -> "솦"
            "media" -> "글"
            "mediamba" -> "미경"
            "sec" -> "정"
            else -> ""
        }

    private companion object {
        const val ADMIN_CANCEL = "reserveCancel"
        const val PHOTO_DELETE = "photoDelete"
        const val PHOTO_EXCEPT = "photoExecpt"
        val DAYS = arrayOf("일", "월", "화", "수", "목", "금", "토")
    }
}
