package kr.ac.ssu.ssutoday.adapter.attestation

import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.ServiceAccountCredentials
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path

@Component
class PlayIntegrityAccessTokenProvider(
    @Value("\${ssutoday.attestation.play-integrity.credentials:}")
    private val credentialsPath: String,
) {
    private var credentials: GoogleCredentials? = null

    /** 최초 사용 시에만 키를 읽으며 GoogleCredentials의 만료 전 토큰 캐시를 재사용한다. */
    @Synchronized
    fun getAccessToken(): String? {
        if (credentialsPath.isBlank()) return null
        val current = credentials ?: loadCredentials()?.also { credentials = it } ?: return null
        current.refreshIfExpired()
        return current.accessToken?.tokenValue
    }

    private fun loadCredentials(): GoogleCredentials? {
        val path = Path.of(credentialsPath)
        if (!Files.isRegularFile(path)) return null
        return Files.newInputStream(path).use {
            ServiceAccountCredentials.fromStream(it).createScoped("https://www.googleapis.com/auth/playintegrity")
        }
    }
}
