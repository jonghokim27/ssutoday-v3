package kr.ac.ssu.ssutoday.api.attest

import jakarta.validation.Valid
import kr.ac.ssu.ssutoday.api.attest.dto.AttestChallengeRequest
import kr.ac.ssu.ssutoday.api.attest.dto.AttestChallengeResponse
import kr.ac.ssu.ssutoday.api.common.ResponseStatus
import kr.ac.ssu.ssutoday.api.config.LoginStudent
import kr.ac.ssu.ssutoday.application.attest.AttestApplicationService
import kr.ac.ssu.ssutoday.application.attest.dto.CreateAttestChallengeCommand
import kr.ac.ssu.ssutoday.core.status.StatusCode
import kr.ac.ssu.ssutoday.domain.student.StudentView
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/attest")
class AttestController(
    private val attestApplicationService: AttestApplicationService,
) {
    @PostMapping("/challenge")
    @ResponseStatus(StatusCode.SSU2000)
    fun createChallenge(
        @LoginStudent student: StudentView,
        @Valid @RequestBody request: AttestChallengeRequest,
    ): AttestChallengeResponse {
        val result =
            attestApplicationService.createChallenge(
                CreateAttestChallengeCommand(student.id, request.purpose, request.reservationId),
            )
        return AttestChallengeResponse(
            result.challenge,
            result.expiresInSeconds,
            result.studentId,
            result.purpose,
            result.reservationId,
        )
    }
}
