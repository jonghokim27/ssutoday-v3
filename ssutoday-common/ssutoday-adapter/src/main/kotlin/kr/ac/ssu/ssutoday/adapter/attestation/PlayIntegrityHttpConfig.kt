package kr.ac.ssu.ssutoday.adapter.attestation

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

@Configuration
class PlayIntegrityHttpConfig {
    @Bean
    fun playIntegrityRestClient(builder: RestClient.Builder): RestClient =
        builder
            .clone()
            .baseUrl("https://playintegrity.googleapis.com")
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(Duration.ofSeconds(3))
                    setReadTimeout(Duration.ofSeconds(5))
                },
            ).build()
}
