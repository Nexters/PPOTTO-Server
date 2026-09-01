<!-- Parent: ../../AGENTS.md -->

# src/main/resources

Configuration and database migrations.

| File / Directory | Description |
|------|-------------|
| `application.yml` | Only app name, `profiles.default: local`, and the `spring.config.import` list. No other keys |
| `config/server.yml` | Port, graceful shutdown, virtual threads enabled; prod adds `forward-headers-strategy: framework` (nginx reverse proxy) |
| `config/datasource.yml` | Postgres connection from `${POSTGRES_*}`; prod overrides hikari pool size |
| `config/flyway.yml` | Flyway settings (`classpath:db/migration`, `out-of-order` enabled for parallel branches) |
| `config/jooq.yml` | jOOQ dialect |
| `config/jackson.yml` | JSON defaults (non_null, Asia/Seoul) |
| `config/actuator.yml` | health/info only; local exposes all with details |
| `config/logging.yml` | Console pattern with `[requestId]` MDC; local raises levels to debug |
| `config/springdoc.yml` | Swagger UI options plus `default-produces-media-type: application/json`, which keeps inferred response media types off `*/*` so generated clients see a single JSON content type |
| `config/cors.yml` | `cors.allowed-origins` from `${CORS_ALLOWED_ORIGINS}`, applied as CORS origin patterns. Dev(`compose.dev.yaml`)는 `*`로 고정해 모든 origin을 허용한다 |
| `config/security.yml` | Basic auth user for swagger from `${SWAGGER_USER}` / `${SWAGGER_PASSWORD}` |
| `config/user.yml` | User-account provider refresh-token encryption key from `${USER_PROVIDER_REFRESH_TOKEN_ENCRYPTION_KEY_BASE64}` plus the `user.withdrawn-cleanup` enable flag, retention days, batch size, and cron from `${USER_WITHDRAWN_CLEANUP_*}` |
| `config/gcs.yml` | `gcs.bucket` / `gcs.credentials-path` / `gcs.upload-signed-url-expiration-minutes` / `gcs.read-signed-url-expiration-minutes` / `gcs.timeout-millis` from `${GCS_*}` |
| `config/vertexai.yml` | `vertexai.project` / `location` plus classify and verify timeout budgets from `${VERTEX_AI_*}` |
| `config/redis.yml` | Redis host, port, password, and timeouts from `${REDIS_*}` for refresh token storage |
| `config/sentry.yml` | Sentry DSN, environment, release, and traces sample rate from `${SENTRY_*}`, plus full-capture data settings (`send-default-pii: true`, `max-request-body-size: always`), Sentry Logs (`logs.enabled` + `logging.minimum-level: info`), continuous profiling (`profile-session-sample-rate` + a writable `profiling-traces-dir-path`), and logback bridge levels (`error` → event, `info` → breadcrumb). Capture is deliberately wide; secrets in span attributes are masked in code by `global/observability`, not by org scrubbing. An empty `SENTRY_DSN` leaves the SDK initialized but inactive, which is the local/test default |
| `config/auth.yml` | OAuth HTTP Service client group timeouts (`spring.http.serviceclient.oauth.*` from `${OAUTH_*_TIMEOUT_MILLIS}`, bare integers bind as milliseconds), Kakao, Apple, service JWT, and token expiration settings from provider/auth env vars |
| `db/migration/` | Flyway timestamp migrations. The base schema creates core tables; later migrations add legacy-compatible social accounts, terms, drawings, stickers, recap data, active-analysis index updates, the six-stickers-per-analysis guard, the recap one-line summary column that replaced `recap_comments.is_float`, and the drawing `type` discriminator with the text columns and the `z_index` promotion that backfills from the existing `stroke` JSON |

## Rules

- One concern per config file. New concerns get a new `config/<concern>.yml` plus an entry in `application.yml` imports. No duplicate keys across files (later imports override earlier ones).
- Profile differences live inside each concern file as a `---` document with `spring.config.activate.on-profile`. Profiles: `local` (default), `prod`, `test` (test resources only).
- Placeholders never carry defaults (`${VAR}`, not `${VAR:value}`). Add every new variable to **all three** of `.env.template`, `src/test/resources/application-test.yml`, and the AOT cache training `RUN` step in the root `Dockerfile`, in the same change. Forgetting the `Dockerfile` still passes `./gradlew build`, so the failure only surfaces in CD — see the root `AGENTS.md` Conventions section for the dummy-value rules.
- `USER_PROVIDER_REFRESH_TOKEN_ENCRYPTION_KEY_BASE64` must decode to a 256-bit AES key. The committed template/test value is local-only and must be replaced in deployed environments.
- `USER_WITHDRAWN_CLEANUP_ENABLED` defaults to `false` everywhere, including tests. Turning it on schedules irreversible hard deletion, so only enable it in an environment where the retention policy is agreed. `USER_WITHDRAWN_CLEANUP_RETENTION_DAYS` is a conservative placeholder until the privacy policy fixes a number.
- After legacy users are backfilled with real provider identity and email, validate `ck_users_social_identity_complete` and only then consider converting the three columns to physical `NOT NULL`.

Update this file when config files or migration conventions change.
