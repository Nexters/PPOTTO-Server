<!-- Parent: ../AGENTS.md -->

# global.config

Spring configuration beans.

| File | Description |
|------|-------------|
| `SecurityConfig.kt` | Swagger chain (`@Order(0)`, prod Basic), stateless JWT Bearer API chain (`@Order(100)`, `@Profile("!test")`), test-only permit-all fallback chain (`@Profile("test")`), and fluent CORS source construction. Login, refresh, health, swagger, and `GET /terms` are public; all other API requests require authentication |
| `CorsProperties.kt` | `@ConfigurationProperties("cors")` + `@Validated`. Bound from `CORS_ALLOWED_ORIGINS` |
| `OpenApiConfig.kt` | Swagger metadata, response envelope contract, common error table, Bearer scheme, shared API version header, and fluent required/optional authentication documentation inferred from controller argument annotations |
| `WebMvcConfig.kt` | `X-API-Version` header versioning with default `1` and `CurrentUserArgumentResolver` registration |
| `GcsProperties.kt` | `@ConfigurationProperties("gcs")` + `@Validated`. `bucket`, `credentialsPath`, `uploadSignedUrlExpirationMinutes` (15 min per spec — named `upload*` since read/GET signed URLs, when added, need a different 1-hour expiration and can't share this property), `timeoutMillis` — bound from `GCS_*` env vars |
| `GcsConfig.kt` | Defines the `Storage` bean. Authenticates by reading the service account key file (`gcsProperties.credentialsPath`) via `ServiceAccountCredentials.fromStream` — not the `GOOGLE_APPLICATION_CREDENTIALS` ambient method. Sets connect/read timeout (`gcsProperties.timeoutMillis`) via `HttpTransportOptions` so a slow/hanging GCS call can't block indefinitely |

## Rules

- New `XxxProperties` classes follow the CorsProperties pattern: data class, constructor binding, `@Validated` + jakarta constraints. Separate annotated constructor property groups with one blank line.
- The JWT chain stays at `API_CHAIN_ORDER`; keep swagger Basic auth ahead of it and the fallback chain last.
- The API chain and the permit-all fallback chain both match any request, so their profiles must stay mutually exclusive (`@Profile("!test")` / `@Profile("test")`). If both are ever active, Spring Security aborts context refresh with `UnreachableFilterChainException` — which no test catches, because tests only run the `test` profile.
- 401/403 from the JWT chain use the same `ApiResponse` error envelope through auth security handlers.
- `GET /terms` alone permits anonymous access. `POST /terms/agreements` remains protected.
- Don't mark beans that read local files/external credentials (e.g. `GcsConfig.storage`) `@Lazy`. Every integration test in this repo (extending `IntegrationTest`) boots the **entire** application context via `@SpringBootTest`, so instead of scattering `@Lazy` through production code to dodge test startup, provide a real, parseable dummy credentials file in tests (`src/test/resources/dummy-gcs-key.json`) so the bean can be created eagerly as normal. Building the `Storage` bean and V4-signing are local credential parsing / RSA signing operations with no network call, so a dummy key is enough. Follow this same pattern (keep the real bean as-is, add a dummy test fixture) for any new bean that reads local files or external credentials.

Update this file when configuration beans change.
