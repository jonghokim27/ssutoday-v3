package kr.ac.ssu.ssutoday.api.config

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.module.kotlin.KotlinFeature
import tools.jackson.module.kotlin.KotlinModule

@Configuration
class JacksonConfig {
    @Bean
    fun kotlinJsonMapperCustomizer() =
        JsonMapperBuilderCustomizer { builder ->
            builder.addModule(
                KotlinModule
                    .Builder()
                    .enable(KotlinFeature.KotlinPropertyNameAsImplicitName)
                    .build(),
            )
        }
}
