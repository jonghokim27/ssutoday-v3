package kr.ac.ssu.ssutoday.batch.crawler.dto

data class StudentCouncilPost(
    var postId: Long = 0L,
    var title: String = "",
    var content: String = "",
    var date: String = "",
)
