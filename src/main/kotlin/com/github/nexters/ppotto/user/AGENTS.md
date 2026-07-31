<!-- Parent: ../AGENTS.md -->

# user

User account domain. Owns active social identity uniqueness, encrypted provider refresh tokens, account anonymization, and eventual hard deletion.

| Directory | Description |
|-----------|---------|
| `domain/` | Pure account model, `OAuthProvider`, encrypted-token value type, and `USER-*` errors |
| `application/port/ProviderRefreshTokenCipher.kt` | Plaintext-to-encrypted token boundary; AES-GCM adapter lives in infrastructure |
| `application/port/SocialAccountRevoker.kt` | Provider-account revoke boundary; the auth domain must provide the real adapter |
| `application/port/UserSessionRevoker.kt` | 탈퇴 시 서비스 refresh token을 폐기하는 auth 연동 경계 |
| `application/port/WithdrawnUserDataDeletionPort.kt` | Idempotent cross-domain contract for deleting all DB and object-storage data owned by a withdrawn user |
| `application/UserService.kt` | Transaction boundary for atomic social lookup/create, active account lookup, and session-revoking withdrawal |
| `application/WithdrawnUserCleanupService.kt` | Bounded cleanup batch; hard-deletes a user only after the cross-domain deletion port succeeds |
| `presentation/UserController.kt` | Version 1 `GET /users/me` and `DELETE /users/me` endpoints |
| `presentation/dto/UserResponse.kt` | Public account response without social-provider identifiers or tokens |
| `infrastructure/UserRepository.kt` | DSLContext persistence for active account lookup, profile refresh, withdrawal, and hard deletion |
| `infrastructure/SocialUserRepository.kt` | Atomic active social-account creation using the partial unique index as the conflict target |
| `infrastructure/ProviderRefreshTokenEncryptionProperties.kt` | Validated base64 AES key configuration for provider refresh-token encryption |
| `infrastructure/AesGcmProviderRefreshTokenCipher.kt` | Versioned AES-256-GCM provider refresh-token encryption adapter |
| `infrastructure/UserPortFallbackConfig.kt` | Fail-closed fallback beans used until provider, session, and deletion adapters are integrated |

## Rules

- `id`/`createdAt`/`updatedAt` are DB-generated (`uuidv7()` default, `now()` default, `set_updated_at()` trigger) and read back via `RETURNING`.
- A provider refresh token crosses persistence only as `EncryptedProviderRefreshToken`; plaintext encryption/decryption belongs to an application port adapter.
- Active account lookup always includes `deleted_at IS NULL`. Withdrawal anonymizes email and clears the provider refresh token before setting `deleted_at`.
- Concurrent social signup uses the active-identity partial unique index as the conflict target, then reloads the winner instead of surfacing a unique violation.
- Pre-social legacy rows remain nullable under the unvalidated completeness check and are excluded from application lookup until a real-identity backfill is completed. New social users always write provider, provider user id, and email together.
- Controllers consume the auth domain's UUID principal through nullable `@AuthenticationPrincipal`; absence returns `COMMON-004`.
- Missing auth adapters fail closed: provider-account or session revoke aborts withdrawal.
- Withdrawal revokes the service refresh-token session inside the user transaction.
- The cleanup caller supplies the retention cutoff. A scheduler must not invent a retention period; wire the approved policy and an idempotent cross-domain deletion adapter during integration.

Update this file when layers are added to this domain.
