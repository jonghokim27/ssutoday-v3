package kr.ac.ssu.ssutoday.api.common

import io.github.oshai.kotlinlogging.KotlinLogging
import kr.ac.ssu.ssutoday.core.exception.BusinessException
import kr.ac.ssu.ssutoday.core.status.StatusCode
import org.springframework.context.MessageSource
import org.springframework.core.MethodParameter
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice
import org.springframework.web.servlet.resource.NoResourceFoundException
import org.springframework.web.multipart.support.MissingServletRequestPartException

@RestControllerAdvice
class GlobalControllerAdvice(
    private val messageSource: MessageSource,
) : ResponseBodyAdvice<Any> {
    override fun supports(
        returnType: MethodParameter,
        converterType: Class<out HttpMessageConverter<*>>,
    ): Boolean = returnType.hasMethodAnnotation(ResponseStatus::class.java)

    override fun beforeBodyWrite(
        body: Any?,
        returnType: MethodParameter,
        selectedContentType: MediaType,
        selectedConverterType: Class<out HttpMessageConverter<*>>,
        request: ServerHttpRequest,
        response: ServerHttpResponse,
    ): ApiResponse<Any> {
        val declaredStatus = requireNotNull(returnType.getMethodAnnotation(ResponseStatus::class.java)).status
        val status = body as? StatusCode ?: declaredStatus
        val data = body.takeUnless { it == Unit || it is StatusCode }
        response.setStatusCode(status.httpStatus())
        return ApiResponse.of(status, data, messageSource)
    }

    @ExceptionHandler(BusinessException::class)
    fun business(exception: BusinessException): ResponseEntity<ApiResponse<Nothing>> {
        logger.debug(exception) { "Business exception: ${exception.code}" }
        return errorResponse(exception.status)
    }

    @ExceptionHandler(
        MethodArgumentNotValidException::class,
        HttpMessageNotReadableException::class,
        MissingServletRequestParameterException::class,
        MissingServletRequestPartException::class,
        HttpMediaTypeNotSupportedException::class,
    )
    fun badRequest(exception: Exception): ResponseEntity<ApiResponse<Nothing>> {
        logger.debug(exception) { "Invalid API request" }
        return errorResponse(StatusCode.SSU4000)
    }

    @ExceptionHandler(
        NoHandlerFoundException::class,
        NoResourceFoundException::class,
        HttpRequestMethodNotSupportedException::class,
    )
    fun notFound(exception: Exception): ResponseEntity<ApiResponse<Nothing>> {
        logger.debug(exception) { "API endpoint not found" }
        return errorResponse(StatusCode.SSU4004)
    }

    @ExceptionHandler(Exception::class)
    fun internal(exception: Exception): ResponseEntity<ApiResponse<Nothing>> {
        logger.error(exception) { "Unhandled API exception" }
        return errorResponse(StatusCode.SSU5000)
    }

    private fun errorResponse(status: StatusCode): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(status.httpStatus()).body(ApiResponse.of(status, null, messageSource))

    private companion object {
        val logger = KotlinLogging.logger {}
    }
}
