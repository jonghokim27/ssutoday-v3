package kr.ac.ssu.ssutoday.domain.student.factory

import kr.ac.ssu.ssutoday.domain.student.RefreshToken
import kr.ac.ssu.ssutoday.domain.student.RefreshTokenView
import kr.ac.ssu.ssutoday.domain.student.Student
import kr.ac.ssu.ssutoday.domain.student.StudentView

fun Student.toView() =
    StudentView(
        id = id,
        name = name,
        major = major,
        isAdmin = isAdmin == 1,
    )

fun RefreshToken.toView() =
    RefreshTokenView(
        refreshToken = refreshToken,
        accessToken = accessToken,
        studentId = studentId,
        name = name,
        major = major,
    )
