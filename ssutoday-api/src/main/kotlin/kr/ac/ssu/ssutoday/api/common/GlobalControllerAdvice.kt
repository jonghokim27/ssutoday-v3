package kr.ac.ssu.ssutoday.api.common

import io.github.oshai.kotlinlogging.KotlinLogging
import kr.ac.ssu.ssutoday.api.common.dto.ApiResponse
import kr.ac.ssu.ssutoday.core.exception.BusinessException
import kr.ac.ssu.ssutoday.core.status.SsuStatus
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
    ): Boolean = returnType.hasMethodAnnotation(SsuResponse::class.java)

    override fun beforeBodyWrite(
        body: Any?,
        returnType: MethodParameter,
        selectedContentType: MediaType,
        selectedConverterType: Class<out HttpMessageConverter<*>>,
        request: ServerHttpRequest,
        response: ServerHttpResponse,
    ): ApiResponse<Any> {
        val declaredStatus = requireNotNull(returnType.getMethodAnnotation(SsuResponse::class.java)).status
        val status = body as? SsuStatus ?: declaredStatus
        val data = body.takeUnless { it == Unit || it is SsuStatus }
        response.setStatusCode(status.httpStatus())
        return apiResponse(status, data, messageSource)
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
        return errorResponse(SsuStatus.SSU4000)
    }

    @ExceptionHandler(
        NoHandlerFoundException::class,
        NoResourceFoundException::class,
        HttpRequestMethodNotSupportedException::class,
    )
    fun notFound(exception: Exception): ResponseEntity<ApiResponse<Nothing>> {
        logger.debug(exception) { "API endpoint not found" }
        return errorResponse(SsuStatus.SSU4004)
    }

    @ExceptionHandler(Exception::class)
    fun internal(exception: Exception): ResponseEntity<ApiResponse<Nothing>> {
        logger.error(exception) { "Unhandled API exception" }
        return errorResponse(SsuStatus.SSU5000)
    }

    private fun errorResponse(status: SsuStatus): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(status.httpStatus()).body(apiResponse(status, null, messageSource))

    private companion object {
        val logger = KotlinLogging.logger {}
    }
}
