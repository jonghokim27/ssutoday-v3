package kr.ac.ssu.ssutoday.api.common

import kr.ac.ssu.ssutoday.core.status.SsuStatus

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class SsuResponse(
    val status: SsuStatus,
)
