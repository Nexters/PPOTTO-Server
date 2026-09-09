package com.github.nexters.ppotto.notification.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.io.FileInputStream

@Configuration(proxyBeanMethods = false)
@Profile("!test")
class FcmConfig {
    @Bean
    fun firebaseMessaging(fcmProperties: FcmProperties): FirebaseMessaging {
        val options =
            FirebaseOptions
                .builder()
                .setCredentials(
                    FileInputStream(fcmProperties.credentialsPath).use { GoogleCredentials.fromStream(it) },
                ).setConnectTimeout(fcmProperties.timeoutMillis.toInt())
                .setReadTimeout(fcmProperties.timeoutMillis.toInt())
                .build()
        val app = FirebaseApp.getApps().firstOrNull() ?: FirebaseApp.initializeApp(options)
        return FirebaseMessaging.getInstance(app)
    }
}
