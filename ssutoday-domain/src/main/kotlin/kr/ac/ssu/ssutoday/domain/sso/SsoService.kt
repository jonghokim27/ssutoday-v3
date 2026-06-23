package kr.ac.ssu.ssutoday.domain.sso

import kr.ac.ssu.ssutoday.core.exception.BusinessException
import kr.ac.ssu.ssutoday.core.status.StatusCode
import kr.ac.ssu.ssutoday.domain.sso.factory.toView
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class SsoService(
    private val clients: SsoClientRepository,
    private val tokens: SsoTokenRepository,
) {
    fun getClient(clientId: String): SsoClientView =
        (clients.findByIdOrNull(clientId)
            ?: throw BusinessException(StatusCode.SSU4150))
            .toView()

    fun authenticateClient(clientId: String, clientSecret: String): SsoClientView =
        clients.findByIdAndSecret(clientId, clientSecret)?.toView()
            ?: throw BusinessException(StatusCode.SSU4001)

    fun saveToken(token: String, clientId: String, studentId: Int, name: String, major: String) {
        tokens.save(SsoToken(token, clientId, studentId, name, major))
    }

    fun getToken(token: String): SsoTokenView =
        (tokens.findByIdOrNull(token)
            ?: throw BusinessException(StatusCode.SSU4160))
            .toView()

    fun deleteToken(token: String) {
        tokens.deleteById(token)
    }
}
