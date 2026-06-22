package kr.ac.ssu.ssutoday.core.exception

import kr.ac.ssu.ssutoday.core.status.SsuStatus

class BusinessException(
    val status: SsuStatus,
    val arguments: Array<out Any> = emptyArray(),
    cause: Throwable? = null,
) : RuntimeException(status.code, cause) {
    val code: String
        get() = status.code
}
