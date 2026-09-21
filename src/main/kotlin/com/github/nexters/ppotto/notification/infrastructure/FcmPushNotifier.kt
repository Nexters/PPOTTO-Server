package com.github.nexters.ppotto.notification.infrastructure

import com.github.nexters.ppotto.notification.application.port.PushNotifier
import com.github.nexters.ppotto.notification.application.port.PushSendResult
import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.AndroidNotification
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.MulticastMessage
import com.google.firebase.messaging.Notification
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("!test")
class FcmPushNotifier(
    private val firebaseMessaging: FirebaseMessaging,
) : PushNotifier {
    override fun sendToTokens(
        tokens: List<String>,
        title: String,
        body: String,
        data: Map<String, String>,
    ): List<PushSendResult> {
        val message =
            MulticastMessage
                .builder()
                .addAllTokens(tokens)
                .setNotification(
                    Notification
                        .builder()
                        .setBody(body)
                        .build(),
                ).setAndroidConfig(
                    AndroidConfig
                        .builder()
                        .setNotification(
                            AndroidNotification
                                .builder()
                                .setTitle(title)
                                .setBody(body)
                                .build(),
                        ).build(),
                ).putAllData(data)
                .build()

        val response = firebaseMessaging.sendEachForMulticast(message)
        return tokens.zip(response.responses) { token, sendResponse ->
            PushSendResult(
                token = token,
                success = sendResponse.isSuccessful,
                invalid =
                    sendResponse.exception
                        ?.messagingErrorCode
                        .marksTokenInvalid(),
            )
        }
    }
}

internal fun MessagingErrorCode?.marksTokenInvalid(): Boolean =
    this == MessagingErrorCode.UNREGISTERED || this == MessagingErrorCode.SENDER_ID_MISMATCH
