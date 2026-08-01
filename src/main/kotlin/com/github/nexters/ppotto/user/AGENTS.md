<!-- Parent: ../AGENTS.md -->

# user

User account domain. Owns active social identity uniqueness, encrypted provider refresh tokens, account anonymization, and eventual hard deletion.

| Directory | Description |
|-----------|---------|
| `domain/` | Pure account model with expression-bodied validation and withdrawal transition, `OAuthProvider`, encrypted-token value type, and `USER-*` errors |
| `application/port/ProviderRefreshTokenCipher.kt` | Plaintext-to-encrypted token boundary; AES-GCM adapter lives in infrastructure |
| `application/port/SocialAccountRevoker.kt` | Provider-account revoke boundary; the auth domain must provide the real adapter |
| `application/port/UserSessionRevoker.kt` | Auth integration boundary that revokes the service refresh token on withdrawal |
| `application/port/WithdrawnUserDataDeletionPort.kt` | Idempotent cross-domain contract for deleting all DB and object-storage data owned by a withdrawn user |
| `application/port/WithdrawnUserDataPorts.kt` | Per-provider deletion contracts the composite adapter fans out to: board(+drawing), sticker(+recap, sticker image objects), analysis(+photo, original GCS objects), term agreement |
| `application/UserService.kt` | Expression-bodied transaction pipeline for atomic social lookup/create, active account lookup, and session-revoking withdrawal |
| `application/WithdrawnUserCleanupService.kt` | Fluent bounded cleanup pipeline; hard-deletes a user only after the cross-domain deletion port succeeds |
| `presentation/UserApi.kt` | Version 1 `GET /users/me` and `DELETE /users/me` mapping and Swagger contract |
| `presentation/UserController.kt` | Fluent User API implementation with required UUID user injection |
| `presentation/dto/UserResponse.kt` | Swagger-described public account response without social-provider identifiers or tokens |
| `presentation/UserApiExamples.kt` | `ApiExampleProvider` implementation. Defines Kakao-user and Apple private-relay-user lookup response examples as real DTO instances |
| `infrastructure/UserRepository.kt` | Fluent DSLContext persistence for active account lookup, profile refresh, withdrawal, and hard deletion |
| `infrastructure/SocialUserRepository.kt` | Atomic active social-account creation using the partial unique index as the conflict target |
| `infrastructure/ProviderRefreshTokenEncryptionProperties.kt` | Validated base64 AES key configuration following the shared constructor property spacing convention |
| `infrastructure/AesGcmProviderRefreshTokenCipher.kt` | Fluent versioned AES-256-GCM provider refresh-token encryption adapter |
| `infrastructure/UserPortFallbackConfig.kt` | Fail-closed fallback beans used until provider and session adapters are integrated; the deletion fallback now backs off for the production composite adapter |
| `infrastructure/integration/WithdrawnUserDataDeletionAdapter.kt` | Production `WithdrawnUserDataDeletionPort` composite that only orders the per-provider deletion ports and owns no persistence itself |
| `infrastructure/WithdrawnUserCleanupProperties.kt` | Validated `user.withdrawn-cleanup` enable flag, retention days, batch size, and cron |
| `infrastructure/WithdrawnUserCleanupScheduler.kt` | Property-gated `@EnableScheduling` entry point that turns the retention policy into a `deletedBefore` cutoff |

## Rules

- `id`/`createdAt`/`updatedAt` are DB-generated (`uuidv7()` default, `now()` default, `set_updated_at()` trigger) and read back via `RETURNING`.
- A provider refresh token crosses persistence only as `EncryptedProviderRefreshToken`; plaintext encryption/decryption belongs to an application port adapter.
- Active account lookup always includes `deleted_at IS NULL`. Withdrawal anonymizes email and clears the provider refresh token before setting `deleted_at`.
- Concurrent social signup uses the active-identity partial unique index as the conflict target, then reloads the winner instead of surfacing a unique violation.
- Pre-social legacy rows remain nullable under the unvalidated completeness check and are excluded from application lookup until a real-identity backfill is completed. New social users always write provider, provider user id, and email together.
- Controllers consume the UUID principal through the shared `@AuthenticatedUser` contract; absence returns `COMMON-004` before controller execution. The controller wraps it into `UserId` before calling the application service.
- `User.id`, repository public signatures, application services, and every `application/port` contract use the typed `UserId`/`BoardId` from `global/identifier`; raw `UUID` appears only in jOOQ DSL bindings inside repositories and presentation DTOs.
- Missing auth adapters fail closed: provider-account or session revoke aborts withdrawal.
- Withdrawal revokes the service refresh-token session inside the user transaction.
- The cleanup caller supplies the retention cutoff. `docs/` defines a retention grace period but no number, so `user.withdrawn-cleanup.retention-days` carries it as configuration; replace the conservative default once the privacy policy fixes a value.
- `WithdrawnUserDataDeletionAdapter` never touches another domain's repository. It only sequences the ports in `WithdrawnUserDataPorts.kt`, whose adapters live in the providing domain's `infrastructure/integration/` and call that domain's application service.
- Deletion order is fixed by foreign keys: stickers (with `sticker_photos`/`recap_comments`) → analysis and photos → drawings and boards → term agreements → the user row. `term_agreements.user_id` has a real FK, so skipping it makes `hardDelete` fail.
- Every provider deletes its object-storage objects before its own rows, and each provider commits its own transaction. A partial failure therefore leaves the user soft-deleted with the port's work partly done; the port is idempotent, so the next batch run re-runs the whole sequence and converges. `WithdrawnUserCleanupService` still hard-deletes the user only after the port returns.
- The scheduler is disabled by default (`user.withdrawn-cleanup.enabled=false`) so a misconfigured environment can never mass-delete. Scheduling itself is only enabled together with the flag.

Update this file when layers are added to this domain.
