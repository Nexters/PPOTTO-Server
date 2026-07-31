package com.github.nexters.ppotto.global.config

import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.genai.Client
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.FileInputStream

@Configuration
class VertexAiConfig {
    @Bean
    fun genAiClient(
        vertexAiProperties: VertexAiProperties,
        gcsProperties: GcsProperties,
    ): Client =
        Client
            .builder()
            .vertexAI(true)
            .project(vertexAiProperties.project)
            .location(vertexAiProperties.location)
            .credentials(
                FileInputStream(gcsProperties.credentialsPath)
                    .use { ServiceAccountCredentials.fromStream(it) }
                    .createScoped(CLOUD_PLATFORM_SCOPE),
            ).build()

    companion object {
        private val CLOUD_PLATFORM_SCOPE = listOf("https://www.googleapis.com/auth/cloud-platform")
    }
}
