<!-- Parent: ../AGENTS.md -->

# user

User account domain. Owns active social identity uniqueness, encrypted provider refresh tokens, account anonymization, and eventual hard deletion.

| Directory | Description |
|-----------|---------|
| `domain/` | Pure account model with statement-bodied validation and withdrawal transition, encrypted-token value type, and `USER-*` errors. The provider enum is the shared `global/oauth/OAuthProvider` |
| `application/port/SocialAccountRevoker.kt` | Provider-account revoke boundary; the auth domain provides the real adapter |
| `application/port/UserSessionRevoker.kt` | Auth integration boundary that revokes the service refresh token on withdrawal |
| `application/port/WithdrawnUserDataPorts.kt` | Per-provider deletion contracts the cleanup service fans out to: board(+drawing), sticker(+recap, sticker image objects), analysis(+photo, original GCS objects), term agreement |
| `application/UserModels.kt` | `SocialUserCommand`, `UserRegistrationResult`, and `WithdrawnUserCleanupResult` — every application-layer value type of this domain |
| `application/UserService.kt` | Atomic social lookup/create, active account lookup, and withdrawal ordering |
| `application/WithdrawnUserCleanupService.kt` | Bounded cleanup batch; orders the four deletion ports itself and hard-deletes a user only after they all succeed. Owns `MAX_CLEANUP_BATCH_SIZE` |
| `presentation/UserApi.kt` | Version 1 `GET /users/me` and `DELETE /users/me` mapping and Swagger contract |
| `presentation/UserController.kt` | User API implementation with required typed user injection |
| `presentation/dto/UserResponse.kt` | Swagger-described public account response without social-provider identifiers or tokens |
| `presentation/UserApiExamples.kt` | `ApiExampleProvider` implementation. Defines Kakao-user and Apple private-relay-user lookup response examples as real DTO instances |
| `infrastructure/UserRepository.kt` | The only DSLContext persistence of this domain: conflict-free active social-account creation, active account lookup, profile refresh, withdrawal, and hard deletion |
| `infrastructure/ProviderRefreshTokenEncryptionProperties.kt` | Validated base64 AES key configuration following the shared constructor property spacing convention |
| `infrastructure/AesGcmProviderRefreshTokenCipher.kt` | Versioned AES-256-GCM provider refresh-token encryption; injected as the concrete class, not behind a port |
| `infrastructure/WithdrawnUserCleanupProperties.kt` | Validated `user.withdrawn-cleanup` enable flag, retention days, batch size, and cron |
| `infrastructure/WithdrawnUserCleanupScheduler.kt` | Property-gated `@EnableScheduling` entry point that turns the retention policy into a `deletedBefore` cutoff |

## Rules

- `id`/`createdAt`/`updatedAt` are DB-generated (`uuidv7()` default, `now()` default, `set_updated_at()` trigger) and read back via `RETURNING`. `User.withdraw` therefore never touches `updatedAt` — a domain copy that set it would be a lie about the stored row.
- A provider refresh token crosses persistence only as `EncryptedProviderRefreshToken`; plaintext encryption/decryption belongs to `AesGcmProviderRefreshTokenCipher`. It is local crypto with one implementation and every test uses the real one, so there is no port interface in front of it.
- Active account lookup always includes `deleted_at IS NULL`. Withdrawal anonymizes email and name and clears the provider refresh token before setting `deleted_at`.
- Concurrent social signup uses the active-identity partial unique index as the conflict target (`saveIfAbsent`), then reloads the winner instead of surfacing a unique violation. `save` exists only for the shared `saveTestUser()` fixture and delegates to `saveIfAbsent`; production signup never calls it.
- When `saveIfAbsent` and the reload both come back empty the account was withdrawn between the two statements. That is a lost race, not a missing user, so `findOrCreate` raises `ConflictException` (`COMMON-006`) and the caller can retry.
- Pre-social legacy rows remain nullable under the unvalidated completeness check and are excluded from application lookup until a real-identity backfill is completed. New social users always write provider, provider user id, email, and name together.
- `users.email` is NOT NULL in the DB but optional in `SocialUserCommand`, because Apple sends the email only on the first authorization. `findOrCreate` creates a user only when email and name are both present, and `updateSocialProfile` keeps the stored email (`SET email = email`) when the command carries none, so a re-login never overwrites it with null.
- `users.name` is NOT NULL with no application-level default. Rows existing before the name migration were backfilled with `홍길동`; `findOrCreate` without a name never creates a user and returns null so the caller decides the failure semantics.
- Controllers consume the authenticated `UserId` through the shared `@AuthenticatedUser` contract; absence returns `COMMON-004` before controller execution.
- `User.id`, repository public signatures, application services, every `application/port` contract, and presentation (handler parameters and `UserResponse.id`) use the typed `UserId`/`BoardId` from `global/identifier`; jOOQ id columns are generated typed (codegen `forcedType`), so repository bindings pass typed ids straight through with no unwrapping.
- Missing auth adapters fail closed at boot, not at the first withdrawal: `SocialAccountRevoker`, `UserSessionRevoker`, and the four deletion ports have exactly one implementation each and no `@ConditionalOnMissingBean` fallback, so an unwired port is a startup failure with a stack trace instead of a runtime `error()` deep inside a batch.
- A provider that answers 4xx is not a failure — Apple rejecting an already-revoked token means the goal state holds, so `AppleOAuthClient.revoke` swallows `HttpClientErrorException` only and lets 5xx or network errors abort as before.
- Withdrawal skips the provider revoke when no refresh token is stored, which leaves the Apple link in place; the account then re-signs-up without a name, so that skip is logged as a warning instead of passing silently.
- **Withdrawal order is: read the account, revoke the provider account over HTTP, write the anonymized row, then revoke the service refresh-token session.** The provider revoke runs before any write, so a revoke failure aborts the withdrawal with nothing changed (fail-closed). The read plus the guarded single-row `UPDATE ... WHERE deleted_at IS NULL` need no transaction — a concurrent withdrawal makes the update return no row and the caller gets `USER-001`. Session revoke goes through `TransactionSynchronizationManager` so it runs `afterCommit` if a caller ever wraps this in a transaction, and inline otherwise. Neither the HTTP call nor the Redis call may move back inside a DB transaction.
- The cleanup caller supplies the retention cutoff. `docs/` defines a retention grace period but no number, so `user.withdrawn-cleanup.retention-days` carries it as configuration; replace the conservative default once the privacy policy fixes a value.
- `WithdrawnUserCleanupService` never touches another domain's repository. It only sequences the ports in `WithdrawnUserDataPorts.kt`, whose adapters live in the providing domain's `infrastructure/integration/` and call that domain's application service. Ordering is application logic, so there is no composite port in between.
- Deletion order is fixed by foreign keys: stickers (with `sticker_photos`/`recap_comments`) → analysis and photos → drawings and boards → term agreements → the user row. `term_agreements.user_id` has a real FK, so skipping it makes `hardDelete` fail.
- Every provider deletes its object-storage objects before its own rows, and each provider commits its own transaction. A partial failure therefore leaves the user soft-deleted with the work partly done; each port is idempotent, so the next batch run re-runs the whole sequence and converges. The user row is hard-deleted only after all four ports return.
- The scheduler is disabled by default (`user.withdrawn-cleanup.enabled=false`) so a misconfigured environment can never mass-delete. Scheduling itself is only enabled together with the flag.

Update this file when layers are added to this domain.
