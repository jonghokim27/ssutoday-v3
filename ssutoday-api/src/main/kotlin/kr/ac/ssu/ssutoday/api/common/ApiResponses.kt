package kr.ac.ssu.ssutoday.api.common

import kr.ac.ssu.ssutoday.api.common.dto.ApiResponse
import kr.ac.ssu.ssutoday.core.status.SsuStatus
import org.springframework.context.MessageSource
import org.springframework.http.HttpStatus
import java.util.Locale

fun SsuStatus.httpStatus(): HttpStatus = when {
    code.startsWith("SSU2") -> HttpStatus.OK
    code.startsWith("SSU4") -> HttpStatus.BAD_REQUEST
    else -> HttpStatus.INTERNAL_SERVER_ERROR
}

fun <T> apiResponse(
    status: SsuStatus,
    data: T?,
    messageSource: MessageSource,
): ApiResponse<T> = ApiResponse(
    statusCode = status.code,
    data = data,
    message = messageSource.getMessage(status.messageKey, null, Locale.ENGLISH),
)
