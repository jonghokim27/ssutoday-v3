package kr.ac.ssu.ssutoday.application.attest

import kr.ac.ssu.ssutoday.application.attest.dto.CreateAttestChallengeCommand
import kr.ac.ssu.ssutoday.core.attestation.AttestationChallengeScope
import kr.ac.ssu.ssutoday.core.attestation.AttestationPurpose
import kr.ac.ssu.ssutoday.core.exception.BusinessException
import kr.ac.ssu.ssutoday.core.exception.InvalidInputException
import kr.ac.ssu.ssutoday.core.status.StatusCode
import kr.ac.ssu.ssutoday.domain.reservation.ReservationService
import kr.ac.ssu.ssutoday.domain.student.AttestChallengeService
import kr.ac.ssu.ssutoday.domain.student.AttestChallengeView
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AttestApplicationServiceTest {
    private val challengeService = mock(AttestChallengeService::class.java)
    private val reservationService = mock(ReservationService::class.java)
    private val service = AttestApplicationService(challengeService, reservationService)

    @Test
    fun `업로드 challenge는 로그인 학생과 유효한 예약에 묶인다`() {
        val scope = AttestationChallengeScope(20260000, AttestationPurpose.VERIFY_PHOTO_UPLOAD, 42)
        `when`(challengeService.create(scope)).thenReturn(AttestChallengeView("challenge", 60))

        val result = service.createChallenge(CreateAttestChallengeCommand(20260000, scope.purpose, 42))

        verify(reservationService).getForPhotoUpload(20260000, 42)
        assertEquals(20260000, result.studentId)
        assertEquals(42L, result.reservationId)
        assertEquals(60L, result.expiresInSeconds)
    }

    @Test
    fun `예약 접근이나 업로드 정책이 거부되면 challenge를 발급하지 않는다`() {
        doThrow(BusinessException(StatusCode.SSU4200)).`when`(reservationService).getForPhotoUpload(20260000, 42)

        val error =
            assertFailsWith<BusinessException> {
                service.createChallenge(CreateAttestChallengeCommand(20260000, AttestationPurpose.VERIFY_PHOTO_UPLOAD, 42))
            }

        assertEquals(StatusCode.SSU4200, error.status)
        verifyNoInteractions(challengeService)
    }

    @Test
    fun `키 등록 challenge에는 예약이 필요하지 않다`() {
        val scope = AttestationChallengeScope(20260000, AttestationPurpose.APP_ATTEST_REGISTER)
        `when`(challengeService.create(scope)).thenReturn(AttestChallengeView("challenge", 60))

        val result = service.createChallenge(CreateAttestChallengeCommand(20260000, scope.purpose))

        assertEquals(scope.purpose, result.purpose)
        assertEquals(null, result.reservationId)
        verifyNoInteractions(reservationService)
    }

    @Test
    fun `용도에 맞지 않는 예약 파라미터는 입력 오류다`() {
        val invalid =
            listOf(
                CreateAttestChallengeCommand(20260000, AttestationPurpose.VERIFY_PHOTO_UPLOAD),
                CreateAttestChallengeCommand(20260000, AttestationPurpose.VERIFY_PHOTO_UPLOAD, 0),
                CreateAttestChallengeCommand(20260000, AttestationPurpose.APP_ATTEST_REGISTER, 42),
            )
        invalid.forEach { command -> assertFailsWith<InvalidInputException> { service.createChallenge(command) } }
        verifyNoInteractions(challengeService, reservationService)
    }
}
