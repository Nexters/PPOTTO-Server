package com.github.nexters.ppotto.global.config

import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.http.HttpTransportOptions
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.FileInputStream

@Configuration
class GcsConfig {
    @Bean
    fun storage(gcsProperties: GcsProperties): Storage =
        StorageOptions
            .newBuilder()
            .setCredentials(
                FileInputStream(gcsProperties.credentialsPath).use { ServiceAccountCredentials.fromStream(it) },
            ).setTransportOptions(
                HttpTransportOptions
                    .newBuilder()
                    .setConnectTimeout(gcsProperties.timeoutMillis.toInt())
                    .setReadTimeout(gcsProperties.timeoutMillis.toInt())
                    .build(),
            ).build()
            .service
}
