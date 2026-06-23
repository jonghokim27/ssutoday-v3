package kr.ac.ssu.ssutoday.application.student

import kr.ac.ssu.ssutoday.application.student.dto.LoginResult
import kr.ac.ssu.ssutoday.application.student.dto.StudentIdentity
import kr.ac.ssu.ssutoday.application.student.dto.ValidationResult
import kr.ac.ssu.ssutoday.core.exception.BusinessException
import kr.ac.ssu.ssutoday.core.exception.TokenExpiredException
import kr.ac.ssu.ssutoday.core.dto.TokenPayload
import kr.ac.ssu.ssutoday.core.port.TokenPort
import kr.ac.ssu.ssutoday.core.port.StudentAuthenticationPort
import kr.ac.ssu.ssutoday.core.status.StatusCode
import kr.ac.ssu.ssutoday.domain.student.StudentService
import kr.ac.ssu.ssutoday.domain.student.StudentView
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class StudentApplicationService(
    private val studentService: StudentService,
    private val studentAuthenticationPort: StudentAuthenticationPort,
    private val tokenPort: TokenPort,
) {
    @Transactional
    fun login(sToken: String, sIdno: Int): LoginResult {
        val authenticated = studentAuthenticationPort.authenticate(sToken, sIdno)
        val identity = StudentIdentity(
            authenticated.id,
            authenticated.name,
            authenticated.major,
            authenticated.status,
        )
        val student = studentService.login(identity.id, identity.name, identity.major)
        return issue(student)
    }

    @Transactional
    fun logout(refreshToken: String) = studentService.deleteRefreshToken(refreshToken)

    @Transactional
    fun validate(accessToken: String, refreshToken: String?): ValidationResult {
        return try {
            val payload = tokenPort.validateAccessToken(accessToken)
            ValidationResult(validateStudent(payload))
        } catch (_: TokenExpiredException) {
            val stored = refreshToken?.let(studentService::findRefreshToken)
                ?: throw BusinessException(StatusCode.SSU4001)
            if (stored.accessToken != accessToken) throw BusinessException(StatusCode.SSU4001)
            val student = validateStudent(TokenPayload(stored.studentId, stored.name, stored.major))
            val renewed = issue(student)
            studentService.deleteRefreshToken(stored.refreshToken)
            ValidationResult(student, renewed.accessToken, renewed.refreshToken)
        }
    }

    @Transactional
    fun updateXnApiToken(studentId: Int, token: String) {
        studentService.updateXnApiToken(studentId, token)
    }

    @Transactional
    fun enrollBiometricsKey(studentId: Int, osType: String, uuid: String, publicKey: String) {
        studentService.enrollBiometricsKey(studentId, osType, uuid, publicKey)
    }

    private fun issue(student: StudentView): LoginResult {
        val payload = TokenPayload(student.id, student.name, student.major)
        val access = tokenPort.createAccessToken(payload)
        val refresh = tokenPort.randomToken()
        studentService.saveRefreshToken(refresh, access, student)
        return LoginResult(access, refresh, student.id, student.name, student.major, student.isAdmin)
    }

    private fun validateStudent(payload: TokenPayload): StudentView {
        val student = studentService.get(payload.studentId)
        if (student.name != payload.name || student.major != payload.major) {
            throw BusinessException(StatusCode.SSU4001)
        }
        return student
    }
}
