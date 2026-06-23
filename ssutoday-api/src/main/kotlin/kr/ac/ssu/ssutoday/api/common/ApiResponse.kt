package kr.ac.ssu.ssutoday.api.common

import kr.ac.ssu.ssutoday.core.status.StatusCode
import org.springframework.context.MessageSource
import org.springframework.http.HttpStatus
import java.util.Locale

data class ApiResponse<T>(
    val statusCode: String,
    val data: T?,
    val message: String,
) {
    companion object {
        fun <T> of(
            status: StatusCode,
            data: T?,
            messageSource: MessageSource,
        ): ApiResponse<T> =
            ApiResponse(
                statusCode = status.code,
                data = data,
                message = messageSource.getMessage(status.messageKey, null, Locale.ENGLISH),
            )
    }
}

fun StatusCode.httpStatus(): HttpStatus =
    when {
        code.startsWith("SSU2") -> HttpStatus.OK
        code.startsWith("SSU4") -> HttpStatus.BAD_REQUEST
        else -> HttpStatus.INTERNAL_SERVER_ERROR
    }
