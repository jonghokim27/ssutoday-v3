package kr.ac.ssu.ssutoday.adapter.auth

import io.github.oshai.kotlinlogging.KotlinLogging
import kr.ac.ssu.ssutoday.core.dto.ExternalStudentIdentity
import kr.ac.ssu.ssutoday.core.exception.BusinessException
import kr.ac.ssu.ssutoday.core.port.StudentAuthenticationPort
import kr.ac.ssu.ssutoday.core.status.StatusCode
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class UsaintAuthenticationAdapter(
    @Value("\${ssutoday.usaint.sso-url:https://saint.ssu.ac.kr/webSSO/sso.jsp}")
    private val ssoUrl: String,
    @Value("\${ssutoday.usaint.portal-url:https://saint.ssu.ac.kr/webSSUMain/main_student.jsp}")
    private val portalUrl: String,
) : StudentAuthenticationPort {
    private val client = RestTemplate()
    private val log = KotlinLogging.logger {}

    override fun authenticate(
        sToken: String,
        sIdno: Int,
    ): ExternalStudentIdentity {
        val headers = HttpHeaders().apply { add(HttpHeaders.COOKIE, "sToken=$sToken; sIdno=$sIdno") }
        val sso = client.exchange("$ssoUrl?sToken=$sToken&sIdno=$sIdno", HttpMethod.GET, HttpEntity<Unit>(headers), String::class.java)
        if (!sso.body.orEmpty().contains("""location.href = "/irj/portal";""")) {
            log.error { "Student authentication with sToken $sToken and sIdno $sIdno failed." }
            throw BusinessException(StatusCode.SSU4010)
        }

        val cookies = sso.headers[HttpHeaders.SET_COOKIE].orEmpty().joinToString("; ") { it.substringBefore(";") }
        val portalHeaders = HttpHeaders().apply { add(HttpHeaders.COOKIE, cookies) }
        val html =
            client.exchange(portalUrl, HttpMethod.GET, HttpEntity<Unit>(portalHeaders), String::class.java).body
                ?: errorWithBody("uSaint portal response body is empty", "")

        val document = Jsoup.parse(html)
        val nameBox = document.getElementsByClass("main_box09").first() ?: errorWithBody("uSaintPortalNameBox is null", html)
        val infoBox = document.getElementsByClass("main_box09_con").first() ?: errorWithBody("uSaintPortalInfoBox is null", html)
        val name = parseName(nameBox, html)
        val values = parseValues(infoBox, html)

        return ExternalStudentIdentity(
            id = values.id ?: sIdno,
            name = name,
            major = normalizeMajor(values.major),
            status = normalizeStatus(values.status),
        )
    }

    private fun parseName(
        nameBox: Element,
        html: String,
    ): String =
        nameBox
            .getElementsByTag("span")
            .first()
            ?.text()
            ?.takeIf(String::isNotBlank)
            ?.substringBefore("님")
            ?.trim()
            ?: errorWithBody("uSaintPortalNameBoxSpan is null or empty", html)

    private fun parseValues(
        infoBox: Element,
        html: String,
    ): StudentInfoValues {
        val values = StudentInfoValues()
        infoBox.getElementsByTag("li").forEach { item ->
            val key = item.getElementsByTag("dt").first()?.text() ?: errorWithBody("dt in uSaintPortalInfoBoxLi is null", html)
            val value =
                item
                    .getElementsByTag("strong")
                    .first()
                    ?.text()
                    ?.takeIf(String::isNotBlank)
                    ?: errorWithBody("strong in uSaintPortalInfoBoxLi is null or empty", html)

            when (key) {
                "학번" -> values.id = value.toIntOrNull() ?: errorWithBody("studentId in strong is not an integer", html)
                "소속" -> values.major = value
                "과정/학기" -> values.status = value
            }
        }
        return values
    }

    private fun normalizeMajor(value: String): String =
        when (value) {
            "컴퓨터학부" -> "cse"
            "글로벌미디어학부" -> "media"
            "미디어경영학과" -> "mediamba"
            else -> throw BusinessException(StatusCode.SSU4011, arrayOf(value))
        }

    private fun normalizeStatus(value: String): String =
        when (value) {
            "학사과정 휴학" -> "LEAVE_OF_ABSENCE"
            "학사과정 재학" -> "ENROLLED"
            "학사과정 졸업" -> "GRADUATED"
            else -> throw BusinessException(StatusCode.SSU4012, arrayOf(value))
        }

    private fun errorWithBody(
        message: String,
        html: String,
    ): Nothing {
        log.error { "$message. body=$html" }
        error(message)
    }

    private data class StudentInfoValues(
        var id: Int? = null,
        var major: String = "",
        var status: String = "",
    )
}
