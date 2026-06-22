package kr.ac.ssu.ssutoday.api.common.dto

data class ApiResponse<T>(
    val statusCode: String,
    val data: T?,
    val message: String,
)
