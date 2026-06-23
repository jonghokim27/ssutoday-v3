package kr.ac.ssu.ssutoday.api.common

import kr.ac.ssu.ssutoday.core.status.StatusCode

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ResponseStatus(
    val status: StatusCode,
)
