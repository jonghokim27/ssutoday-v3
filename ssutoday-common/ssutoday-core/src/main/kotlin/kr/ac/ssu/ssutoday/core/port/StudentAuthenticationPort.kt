package kr.ac.ssu.ssutoday.core.port

import kr.ac.ssu.ssutoday.core.dto.ExternalStudentIdentity

interface StudentAuthenticationPort {
    fun authenticate(sToken: String, sIdno: Int): ExternalStudentIdentity
}
