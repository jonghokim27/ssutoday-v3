package kr.ac.ssu.ssutoday.application.sso

import kr.ac.ssu.ssutoday.application.sso.dto.SsoGenerationResult
import kr.ac.ssu.ssutoday.application.sso.dto.SsoValidationResult
import kr.ac.ssu.ssutoday.core.exception.BusinessException
import kr.ac.ssu.ssutoday.core.port.TokenPort
import kr.ac.ssu.ssutoday.core.status.StatusCode
import kr.ac.ssu.ssutoday.domain.sso.SsoService
import kr.ac.ssu.ssutoday.domain.student.StudentView
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SsoApplicationService(
    private val ssoService: SsoService,
    private val tokenPort: TokenPort,
) {
    @Transactional
    fun generateToken(
        student: StudentView,
        clientId: String,
    ): SsoGenerationResult {
        val client = ssoService.getClient(clientId)
        val token = tokenPort.randomToken()
        ssoService.saveToken(token, client.id, student.id, student.name, student.major)
        return SsoGenerationResult(token, client.callbackUrl)
    }

    @Transactional
    fun validateToken(
        clientId: String,
        clientSecret: String,
        token: String,
    ): SsoValidationResult {
        val client = ssoService.authenticateClient(clientId, clientSecret)
        val stored = ssoService.getToken(token)
        if (stored.clientId != client.id) throw BusinessException(StatusCode.SSU4160)
        ssoService.deleteToken(token)
        return SsoValidationResult(stored.studentId, stored.name, stored.major)
    }
}
