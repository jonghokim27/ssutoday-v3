package kr.ac.ssu.ssutoday.api.common

import jakarta.servlet.http.HttpServletResponse
import kr.ac.ssu.ssutoday.core.status.StatusCode
import org.springframework.context.MessageSource
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class ApiResponseWriter(
    private val objectMapper: ObjectMapper,
    private val messageSource: MessageSource,
) {
    fun write(
        response: HttpServletResponse,
        status: StatusCode,
    ) {
        response.status = status.httpStatus().value()
        response.contentType = "application/json"
        response.writer.write(objectMapper.writeValueAsString(ApiResponse.of(status, null, messageSource)))
    }
}
