package kr.ac.ssu.ssutoday.domain.student

data class StudentView(
    val id: Int,
    val name: String,
    val major: String,
    val isAdmin: Boolean,
)
