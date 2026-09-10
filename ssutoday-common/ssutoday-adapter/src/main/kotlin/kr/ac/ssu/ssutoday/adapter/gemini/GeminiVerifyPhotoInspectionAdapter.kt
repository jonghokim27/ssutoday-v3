package kr.ac.ssu.ssutoday.adapter.gemini

import io.github.oshai.kotlinlogging.KotlinLogging
import kr.ac.ssu.ssutoday.core.dto.PhotoInspection
import kr.ac.ssu.ssutoday.core.port.VerifyPhotoInspectionPort
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.util.Base64

/**
 * Gemini generateContent API로 인증샷이 스터디룸에서 촬영된 것인지 검사한다.
 *
 * 최신 문서는 /v1beta/interactions를 안내하지만, 같은 모델·같은 프롬프트로 실측했을 때
 * generateContent가 2배 이상 빨랐다(p50 1.5초 대 3.7초). 지연이 중요한 경로라 generateContent를 쓴다.
 * generateContent가 폐기되면 이 어댑터만 교체하면 된다.
 */
@Component
class GeminiVerifyPhotoInspectionAdapter(
    restClientBuilder: RestClient.Builder,
    private val objectMapper: ObjectMapper,
    @Value("\${ssutoday.gemini.api-key}")
    private val apiKey: String,
    @Value("\${ssutoday.gemini.model}")
    private val model: String,
) : VerifyPhotoInspectionPort {
    private val log = KotlinLogging.logger {}

    // RestClientConfig의 builder는 싱글톤 빈이고 설정 메서드가 자기 자신을 변형하므로,
    // clone하지 않으면 Turnstile 등 같은 builder를 쓰는 어댑터에 설정이 새어 나간다.
    private val restClient =
        restClientBuilder
            .clone()
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(Duration.ofSeconds(5))
                    setReadTimeout(Duration.ofSeconds(15))
                },
            ).build()

    override fun inspect(imageUrl: String): PhotoInspection? {
        if (apiKey.isBlank()) return null

        return try {
            val image = download(imageUrl) ?: return null
            request(image, contentTypeOf(imageUrl))
        } catch (exception: Exception) {
            log.warn(exception) { "인증샷 자동 검사에 실패했습니다: $imageUrl" }
            null
        }
    }

    private fun download(imageUrl: String): ByteArray? {
        val bytes =
            restClient
                .get()
                .uri(imageUrl)
                .retrieve()
                .body(ByteArray::class.java)

        if (bytes == null || bytes.isEmpty()) {
            log.warn { "인증샷을 내려받지 못했습니다: $imageUrl" }
            return null
        }
        if (bytes.size > MAX_IMAGE_BYTES) {
            log.warn { "인증샷이 너무 커서 검사를 건너뜁니다(${bytes.size} bytes): $imageUrl" }
            return null
        }

        return bytes
    }

    private fun request(
        image: ByteArray,
        contentType: String,
    ): PhotoInspection? {
        val body =
            mapOf(
                "contents" to
                    listOf(
                        mapOf(
                            "parts" to
                                listOf(
                                    mapOf("text" to PROMPT),
                                    mapOf(
                                        "inline_data" to
                                            mapOf(
                                                "mime_type" to contentType,
                                                "data" to Base64.getEncoder().encodeToString(image),
                                            ),
                                    ),
                                ),
                        ),
                    ),
                "generationConfig" to
                    mapOf(
                        "temperature" to 0,
                        "responseMimeType" to "application/json",
                        "responseSchema" to RESPONSE_SCHEMA,
                    ),
            )

        val response =
            restClient
                .post()
                .uri("$BASE_URL/$model:generateContent")
                .header("x-goog-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(body))
                .retrieve()
                .body(String::class.java)
                ?: return null

        val text =
            objectMapper
                .readTree(response)
                .path("candidates")
                .firstOrNull()
                ?.path("content")
                ?.path("parts")
                ?.firstOrNull()
                ?.path("text")
                ?.asString()

        if (text.isNullOrBlank()) {
            log.warn { "Gemini 응답에서 판정 결과를 찾지 못했습니다: ${response.take(300)}" }
            return null
        }

        val verdict = objectMapper.readTree(text)
        return PhotoInspection(
            isStudyRoom = verdict.path("isStudyRoom").asBoolean(true),
            confidence = verdict.path("confidence").asDouble(0.0),
            reason = verdict.path("reason").asString(""),
        )
    }

    private fun contentTypeOf(imageUrl: String): String =
        when {
            imageUrl.endsWith(".png", ignoreCase = true) -> "image/png"
            imageUrl.endsWith(".webp", ignoreCase = true) -> "image/webp"
            else -> "image/jpeg"
        }

    private companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        const val MAX_IMAGE_BYTES = 10 * 1024 * 1024

        val RESPONSE_SCHEMA =
            mapOf(
                "type" to "object",
                "properties" to
                    mapOf(
                        "isStudyRoom" to mapOf("type" to "boolean"),
                        "confidence" to mapOf("type" to "number"),
                        "reason" to mapOf("type" to "string"),
                    ),
                "required" to listOf("isStudyRoom", "confidence", "reason"),
            )

        /**
         * 실제 인증샷 24장으로 3회 반복 측정해 24/24를 얻은 프롬프트다.
         * 판정 축은 "구도가 좁은가"가 아니라 "스터디룸에 입실했음을 특정할 근거가 있는가"다.
         * 스터디룸 공용 책상 상판 묘사가 핵심 근거이므로 시설 책상이 바뀌면 함께 고쳐야 한다.
         */
        val PROMPT =
            """
            너는 대학교 스터디룸 예약 시스템의 인증샷 검사기다.
            주어진 사진이 스터디룸 내부에서 방금 촬영된 인증샷인지 판단해라.

            판정의 핵심 근거는 "이 사진이 스터디룸 안에서 찍혔음을 객관적으로 확신할 수 있는가"다.
            사진에 스터디룸이라고 특정할 수 있는 근거가 하나도 없으면 false다.

            스터디룸을 특정하는 근거는 다음과 같다. 하나라도 뚜렷하게 보이면 true다.
            - 공간 요소: 벽면, 의자, 창문과 블라인드, 화이트보드, 천장 조명, 소화기, 유리 파티션, 출입문
            - 스터디룸 안내문이나 방 번호판
            - 스터디룸 공용 책상 상판. 밝은 회백색에서 연한 베이지에 가까운 색이고, 결이 가늘고 균일하며
              광택이 거의 없는 나무 무늬다. 이 상판이 사진에서 넓게 보이면 스터디룸으로 인정한다.

            반대로 다음은 false다.
            - 노트북, 키보드, 트랙패드, 마우스패드, 케이블, 필기구 같은 개인 물건이 화면을 채우고 있고
              위에 적은 근거가 하나도 없는 경우. 이런 사진은 집이나 카페에서도 똑같이 찍을 수 있어
              입실 증거가 되지 않는다.
            - 책상 상판이 보이더라도 짙은 갈색, 주황빛이 도는 오크, 무늬 없는 단색처럼 위에서 설명한
              스터디룸 상판과 다른 경우.
            - 사진 전체가 디스플레이를 재촬영한 것. 화면 특유의 규칙적인 격자나 모아레 무늬가 사진 전면에
              깔리고 가장자리에 베젤이나 검은 테두리가 보인다. 화면 안에 스터디룸 사진이 들어 있어도 false다.
            - 흔들리거나 초점이 나가 아무것도 식별할 수 없는 경우.
            - 명백한 실외, 또는 강의실이나 카페처럼 스터디룸이 아닌 공간.

            주의: 노트북이나 태블릿이 크게 나오는 것 자체는 문제가 아니다. 그 주변에 스터디룸 공간 요소나
            공용 책상 상판이 함께 보이면 true다. 판단 기준은 기기가 나왔는지가 아니라 스터디룸을 특정할
            근거가 있는지다.

            판단이 애매하면 isStudyRoom을 true로 두고 confidence를 낮춰라. 정상 이용자를 막는 것이 더 큰 손해다.

            {"isStudyRoom": boolean, "confidence": 0~1 사이 숫자, "reason": "한 문장 한국어 근거"} 형태의 JSON만 출력해라.
            """.trimIndent()
    }
}
