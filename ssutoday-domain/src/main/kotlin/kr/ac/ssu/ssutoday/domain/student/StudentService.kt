package kr.ac.ssu.ssutoday.domain.student

import kr.ac.ssu.ssutoday.core.exception.BusinessException
import kr.ac.ssu.ssutoday.core.status.StatusCode
import kr.ac.ssu.ssutoday.domain.student.factory.toView
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class StudentService(
    private val students: StudentRepository,
    private val refreshTokens: RefreshTokenRepository,
) {
    fun login(
        id: Int,
        name: String,
        major: String,
    ): StudentView {
        val student =
            students
                .findByIdOrNull(id)
                ?.apply { updateProfile(name, major) }
                ?: Student(id, name, major)
        return students.save(student).toView()
    }

    fun get(id: Int): StudentView =
        (
            students.findByIdOrNull(id)
                ?: throw BusinessException(StatusCode.SSU4001)
        ).toView()

    fun updateXnApiToken(
        studentId: Int,
        token: String,
    ) {
        students.getReferenceById(studentId).xnApiToken = token
    }

    fun saveRefreshToken(
        refreshToken: String,
        accessToken: String,
        student: StudentView,
    ) {
        refreshTokens.save(
            RefreshToken(
                refreshToken = refreshToken,
                accessToken = accessToken,
                studentId = student.id,
                name = student.name,
                major = student.major,
            ),
        )
    }

    fun findRefreshToken(refreshToken: String): RefreshTokenView? = refreshTokens.findByIdOrNull(refreshToken)?.toView()

    fun deleteRefreshToken(refreshToken: String) {
        refreshTokens.deleteById(refreshToken)
    }
}
