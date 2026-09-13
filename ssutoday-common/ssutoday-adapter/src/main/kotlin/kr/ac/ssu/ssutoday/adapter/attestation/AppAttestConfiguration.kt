package kr.ac.ssu.ssutoday.adapter.attestation

import kr.ac.ssu.ssutoday.core.port.AppAttestVerificationPort
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AppAttestConfiguration {
    @Bean
    fun appAttestVerificationPort(
        @Value("\${ssutoday.attestation.app-attest.team-id}") teamId: String,
        @Value("\${ssutoday.attestation.app-attest.bundle-id}") bundleId: String,
        @Value("\${ssutoday.attestation.app-attest.production:true}") production: Boolean,
    ): AppAttestVerificationPort {
        require(Regex("[A-Z0-9]{10}").matches(teamId))
        require(Regex("[A-Za-z0-9]+(?:[.-][A-Za-z0-9]+)+").matches(bundleId))
        return AppAttestVerificationAdapter("$teamId.$bundleId", production)
    }
}
