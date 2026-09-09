<!-- Parent: ../AGENTS.md -->

# notification

Notification domain. Owns per-device FCM token registration and outbound push delivery for events other domains publish.

| Directory | Description |
|-----------|-------------|
| `domain/DevicePlatform.kt` | Closed device platform set (`IOS`, `ANDROID`) used end to end, including the request dto |
| `domain/PushNotificationRequestedEvent.kt` | In-process contract other domains publish to request a push; carries the target `UserId`, title, body, and a string data map |
| `config/FcmProperties.kt` | `@ConfigurationProperties("fcm")` + `@Validated`. `credentialsPath` and `timeoutMillis` bound from `FCM_*` env vars |
| `config/FcmConfig.kt` | `@Profile("!test")` Firebase Admin SDK bootstrap: builds `FirebaseMessaging` from `FcmProperties`. Lives here, not in `global/config`, because notification is its only consumer |
| `application/port/PushNotifier.kt` | Outbound push-delivery port plus its `PushSendResult`; the FCM adapter lives in infrastructure |
| `application/DeviceTokenService.kt` | Device-token registration and release use cases |
| `application/PushNotificationEventListener.kt` | `@Async(AsyncConfig.PUSH_NOTIFICATION_TASK_EXECUTOR)` listener that turns a requested event into a send |
| `application/PushNotificationService.kt` | Token lookup, bounded retry with exponential backoff, and invalid-token cleanup |
| `infrastructure/DeviceTokenRepository.kt` | jOOQ persistence for `user_device_tokens`: device-scoped upsert, release, per-user token lookup, and token-scoped delete |
| `infrastructure/FcmPushNotifier.kt` | `@Profile("!test")` Firebase multicast adapter that maps each per-token response to a `PushSendResult` |
| `presentation/DeviceTokenApi.kt` | Version 1+ `POST /device-tokens` and `DELETE /device-tokens` mapping and Swagger contract |
| `presentation/DeviceTokenController.kt` | Device token API implementation with required typed user injection |
| `presentation/DeviceTokenApiExamples.kt` | `ApiExampleProvider` implementation. Defines the iOS registration request and empty-success responses as real DTO instances |
| `presentation/dto/RegisterDeviceTokenRequest.kt` | Swagger-described registration request; `platform` is the domain enum, not a string |

## Rules

- `id`/`created_at`/`updated_at` are DB-generated (`uuidv7()` default, `now()` default, `set_updated_at()` trigger). The application never sets them.
- `(user_id, device_id)` is unique, so registration is an upsert on that pair. A rotated FCM token for the same `deviceId` updates the existing row instead of adding one.
- Other domains never call `PushNotificationService`. They publish `PushNotificationRequestedEvent`, and the listener runs it on the named `pushNotificationTaskExecutor`, which the test profile replaces with a `SyncTaskExecutor` so tests need no waiting.
- Push delivery never fails the publishing request. `PushNotificationService` retries up to five times with exponential backoff, logs the final failure, and returns an empty result rather than throwing.
- **Only `UNREGISTERED` and `SENDER_ID_MISMATCH` mark a token invalid and delete it.** `INVALID_ARGUMENT` must never be added: FCM also returns it for a bad *message* (oversized data, malformed payload), so one bad payload would delete every valid token of that user.
- `user_device_tokens.user_id` carries no FK, so withdrawn-user cleanup does not have to delete tokens before the user row.
- The FCM bean is `@Profile("!test")` (`config/FcmConfig.kt`) and so is `FcmPushNotifier`, so no `PushNotifier` implementation ships in the test profile. The test double is `notification/support/NotificationTestConfig.kt`, a `@Configuration @Profile("test")` in the **test** source tree that registers the recording `FakePushNotifier` as `@Primary`. It is component-scanned, so every Spring test context gets it whether or not it imports the class — production sources carry no test-only no-op notifier.

Update this file when layers are added to this domain.
