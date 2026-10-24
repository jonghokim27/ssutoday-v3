package kr.ac.ssu.ssutoday.application.reservation

import kr.ac.ssu.ssutoday.domain.student.StudentView

/** Discord 알림과 관리자 화면에 쓰는 예약자 표기. 예: 홍길동 (20260000/컴) */
object StudentInfoFormatter {
    fun format(student: StudentView): String = "${student.name} (${student.id}/${majorShortName(student.major)})"

    private fun majorShortName(major: String): String =
        when (major) {
            "cse" -> "컴"
            "sw" -> "솦"
            "media" -> "글"
            "mediamba" -> "미경"
            "sec" -> "정"
            else -> ""
        }
}
