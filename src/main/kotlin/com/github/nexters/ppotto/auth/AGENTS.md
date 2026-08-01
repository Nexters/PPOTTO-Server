<!-- Parent: ../AGENTS.md -->

# auth

Social login and service token authentication domain.

| Directory | Description |
|-----------|-------------|
| `domain/` | Provider, login command/result, signup transaction result (`AuthSignup`), token models, AUTH error codes |
| `application/` | Expression-based login / active-user revalidation / refresh / logout chains and the user/terms integration ports. Only the signup section is wrapped in a transaction via `signupTransaction` (`TransactionOperations`) injected with `@Qualifier(SIGNUP_TRANSACTION)` |
| `infrastructure/oauth/` | `KakaoOAuthApi`/`AppleOAuthApi` HTTP Service interfaces (`@GetExchange`/`@PostExchange`, full URL passed as a `URI` method parameter) plus Kakao verification and Apple identity token/JWKS/code exchange/revoke adapters |
| `infrastructure/integration/` | Fluent adapters wiring user signup, active-status lookup, default board, terms, and session integration plus provider account revocation. The signup adapter binds the user and the default board into one unit with `@Transactional` |
| `infrastructure/token/` | Fluent HS256 access JWT and opaque refresh token issuance, Redis rotation adapter |
| `infrastructure/security/` | Expression-bodied Bearer authentication context assembly and exception normalization, UUID principal, 401/403 ApiResponse writer. The filter's servlet auto-registration is disabled, so it only runs inside security chains (`SecurityConfig`'s prod API chain and the test fallback chain), and public-path decisions use the single source `global.config.PublicPaths` |
| `presentation/AuthApi.kt` | `/auth/login`, `/auth/refresh`, `/auth/logout` version 1 mapping and Swagger contract: declares only response codes, descriptions, and schemas — examples are injected by `AuthApiExamples` |
| `presentation/AuthController.kt` | Fluent Auth API implementation with request binding and required UUID logout user injection |
| `presentation/AuthApiExamples.kt` | `ApiExampleProvider` implementation. Defines per-provider login requests, new-signup/re-login responses, and `AUTH-001`~`AUTH-004` failure examples as real DTO instances |
| `presentation/dto/` | Swagger-described authentication request and response schemas |
| `config/` | Validated Kakao, Apple, and JWT properties plus adapter/application bean wiring. `AuthHttpConfig` registers the OAuth HTTP Service proxies via `@ImportHttpServices(group = "oauth")`, and timeouts are applied by the `spring.http.serviceclient.oauth.*` properties. `AuthTransactionConfig` defines the signup-only `signupTransaction` bean (`TransactionOperations`, timeout `SIGNUP_TRANSACTION_TIMEOUT_SECONDS` = 5s). Keep one blank line between constructor property groups |

## Rules

- Never expose provider SDKs or HTTP response types to application/domain.
- Never reference user/terms/board repositories directly; use `application.port` only.
- New signup handles user creation, default board creation, and pending-terms lookup in one transaction. If an intermediate step fails, the users row is rolled back too, so no board-less ghost account is left behind.
- The Apple authorization code exchange failure check must throw inside `signupTransaction.execute`. Moving this exception outside or catching it inside commits a ghost Apple account, so the Apple exchange failure scenario in `AuthSignupRollbackIntegrationTest` pins this with the real transaction boundary.
- The signup transaction uses the dedicated bean from `AuthTransactionConfig`, not Spring's shared default `transactionTemplate`. Without a timeout, waits on the `uk_users_provider_uid` unique index and `pg_advisory_xact_lock` can hold connection-pool connections indefinitely. Injection is pinned with `@Qualifier(SIGNUP_TRANSACTION)` instead of relying on name-based fallback.
- The signup adapter carries its own `@Transactional` boundary instead of relying on the caller's transaction. Even when the port is called on its own, the user and the default board commit or roll back together.
- Provider HTTP calls and token issuance/Redis writes run outside the transaction. Never put `@Transactional` on `login` — it would pin a DB connection for the duration of the provider read timeout.
- Concurrent signups for the same social account are serialized by the `uk_users_provider_uid` partial unique index and `saveIfAbsent` followed by re-lookup. Exactly one request becomes the new signup and the default board is created exactly once.
- The provider enums shared between auth and user, and the response DTOs shared between auth and terms, are converted explicitly in adapters.
- Refresh token reissuance revalidates the active user through the user application service before token issue/rotation.
- Plaintext provider refresh tokens travel only as far as the user port boundary; the user persistence adapter encrypts them immediately.
- Refresh tokens are never stored raw in Redis keys/values; they are identified only by SHA-256 hash.
- OAuth HTTP calls must apply `spring.http.serviceclient.oauth.connect-timeout/read-timeout` (`OAUTH_CONNECT_TIMEOUT_MILLIS`/`OAUTH_READ_TIMEOUT_MILLIS`; unit-less integers are milliseconds).
- `KAKAO_*_URI`/`APPLE_*_URI` are full-URL contracts including the path. Do not split them into base-url plus path; pass them as-is through the HTTP Service interface methods' `URI` parameter.
- HTTP Service interface return types stay nullable. Declaring them non-null turns a 2xx empty body into an NPE instead of a `RestClientException`, escaping the `AUTH-001` conversion path.
- `AuthUser.userId`/`PendingTerm.id` and every port signature use typed ids (`UserId`/`TermId`). The JWT subject string and Redis key/value strings are external representations converted inside `JwtTokenProvider`/`RedisRefreshTokenStore`; the SecurityContext principal stays a raw `UUID` (`verifyAccessToken(...).value` in `BearerTokenAuthenticationFilter`) so `CurrentUserArgumentResolver` and controller signatures keep the presentation `UUID` contract, and controllers wrap.

Update this file when auth packages or contracts change.
