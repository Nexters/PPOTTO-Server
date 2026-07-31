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
| `config/springdoc.yml` | Swagger UI options |
| `config/cors.yml` | `cors.allowed-origins` from `${CORS_ALLOWED_ORIGINS}` |
| `config/security.yml` | Basic auth user for swagger from `${SWAGGER_USER}` / `${SWAGGER_PASSWORD}` |
| `config/user.yml` | User-account provider refresh-token encryption key from `${USER_PROVIDER_REFRESH_TOKEN_ENCRYPTION_KEY_BASE64}` |
| `config/gcs.yml` | `gcs.bucket` / `gcs.credentials-path` / `gcs.upload-signed-url-expiration-minutes` from `${GCS_*}` |
| `config/redis.yml` | Redis host, port, password, and timeouts from `${REDIS_*}` for refresh token storage |
| `config/auth.yml` | OAuth HTTP timeouts, Kakao, Apple, service JWT, and token expiration settings from provider/auth env vars |
| `db/migration/` | Flyway timestamp migrations. The base schema creates core tables; later migrations add legacy-compatible social accounts, terms, drawings, stickers, recap data, and the six-stickers-per-analysis guard |

## Rules

- One concern per config file. New concerns get a new `config/<concern>.yml` plus an entry in `application.yml` imports. No duplicate keys across files (later imports override earlier ones).
- Profile differences live inside each concern file as a `---` document with `spring.config.activate.on-profile`. Profiles: `local` (default), `prod`, `test` (test resources only).
- Placeholders never carry defaults (`${VAR}`, not `${VAR:value}`). Add every new variable to `.env.template` and to `src/test/resources/application-test.yml`.
- `USER_PROVIDER_REFRESH_TOKEN_ENCRYPTION_KEY_BASE64` must decode to a 256-bit AES key. The committed template/test value is local-only and must be replaced in deployed environments.
- After legacy users are backfilled with real provider identity and email, validate `ck_users_social_identity_complete` and only then consider converting the three columns to physical `NOT NULL`.

Update this file when config files or migration conventions change.
