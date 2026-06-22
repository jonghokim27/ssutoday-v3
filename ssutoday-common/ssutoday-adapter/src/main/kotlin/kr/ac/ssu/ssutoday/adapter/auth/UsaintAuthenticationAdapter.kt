package kr.ac.ssu.ssutoday.adapter.auth

import kr.ac.ssu.ssutoday.core.exception.BusinessException
import kr.ac.ssu.ssutoday.core.status.SsuStatus
import kr.ac.ssu.ssutoday.core.dto.ExternalStudentIdentity
import kr.ac.ssu.ssutoday.core.port.StudentAuthenticationPort
import org.jsoup.Jsoup
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
    @Value("\${ssutoday.usaint.portal-url:https://saint.ssu.ac.kr/irj/portal}")
    private val portalUrl: String,
) : StudentAuthenticationPort {
    private val client = RestTemplate()

    override fun authenticate(sToken: String, sIdno: Int): ExternalStudentIdentity {
        val headers = HttpHeaders().apply { add(HttpHeaders.COOKIE, "sToken=$sToken; sIdno=$sIdno") }
        val sso = client.exchange("$ssoUrl?sToken=$sToken&sIdno=$sIdno", HttpMethod.GET, HttpEntity<Unit>(headers), String::class.java)
        if (!sso.body.orEmpty().contains("/irj/portal")) throw BusinessException(SsuStatus.SSU4010)

        val cookies = sso.headers[HttpHeaders.SET_COOKIE].orEmpty().joinToString("; ") { it.substringBefore(";") }
        val portalHeaders = HttpHeaders().apply { add(HttpHeaders.COOKIE, cookies) }
        val html = client.exchange(portalUrl, HttpMethod.GET, HttpEntity<Unit>(portalHeaders), String::class.java).body
            ?: throw BusinessException(SsuStatus.SSU5000)
        val document = Jsoup.parse(html)
        val name = document.selectFirst(".main_box09 span")?.text()?.substringBefore("(")?.trim()
            ?: throw BusinessException(SsuStatus.SSU5000)
        val values = document.select(".main_box09_con li").associate {
            it.selectFirst("dt")?.text().orEmpty() to it.selectFirst("strong")?.text().orEmpty()
        }
        val id = values.entries.firstOrNull { it.key.contains("학번") }?.value?.toIntOrNull() ?: sIdno
        val rawMajor = values.entries.firstOrNull { it.key.contains("소속") }?.value.orEmpty()
        val status = values.entries.firstOrNull { it.key.contains("학적") }?.value.orEmpty()
        return ExternalStudentIdentity(id, name, normalizeMajor(rawMajor), status)
    }

    private fun normalizeMajor(value: String): String = when {
        "전자정보공학부" in value -> "infocom"
        value == "컴퓨터학부" -> "cse"
        value == "소프트웨어학부" -> "sw"
        value == "글로벌미디어학부" -> "media"
        value == "미디어경영학과" -> "mediamba"
        value == "AI융합학부" -> "aix"
        value == "정보보호학과" -> "sec"
        else -> throw BusinessException(SsuStatus.SSU4011, arrayOf(value))
    }
}
