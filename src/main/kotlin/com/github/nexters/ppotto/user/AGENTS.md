<!-- Parent: ../AGENTS.md -->

# user

User account domain. Owns active social identity uniqueness, encrypted provider refresh tokens, account anonymization, and eventual hard deletion.

| Directory | Description |
|-----------|---------|
| `domain/` | Pure account model, `OAuthProvider`, encrypted-token value type, and `USER-*` errors |
| `application/port/ProviderRefreshTokenCipher.kt` | Plaintext-to-encrypted token boundary; AES-GCM adapter lives in infrastructure |
| `application/port/CurrentUserProvider.kt` | Authenticated user-id boundary; the auth domain must provide the real adapter |
| `application/port/SocialAccountRevoker.kt` | Provider-account revoke boundary; the auth domain must provide the real adapter |
| `application/UserService.kt` | Transaction boundary for social lookup/create, account lookup, and withdrawal |
| `infrastructure/UserRepository.kt` | DSLContext persistence for social account creation, active lookup, profile refresh, withdrawal, and hard deletion |
| `infrastructure/ProviderRefreshTokenEncryptionProperties.kt` | Validated base64 AES key configuration for provider refresh-token encryption |
| `infrastructure/AesGcmProviderRefreshTokenCipher.kt` | Versioned AES-256-GCM provider refresh-token encryption adapter |
| `infrastructure/UserPortFallbackConfig.kt` | Fail-closed fallback beans used until auth adapters are integrated |

## Rules

- `id`/`createdAt`/`updatedAt` are DB-generated (`uuidv7()` default, `now()` default, `set_updated_at()` trigger) and read back via `RETURNING`.
- A provider refresh token crosses persistence only as `EncryptedProviderRefreshToken`; plaintext encryption/decryption belongs to an application port adapter.
- Active account lookup always includes `deleted_at IS NULL`. Withdrawal anonymizes email and clears the provider refresh token before setting `deleted_at`.
- Missing auth adapters fail closed: current-user lookup returns `COMMON-002`, and provider-account revoke aborts Apple withdrawal.

Update this file when layers are added to this domain.
