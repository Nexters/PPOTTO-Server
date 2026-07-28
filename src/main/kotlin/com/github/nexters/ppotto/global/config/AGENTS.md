<!-- Parent: ../AGENTS.md -->

# global.config

Spring configuration beans.

| File | Description |
|------|-------------|
| `SecurityConfig.kt` | Two scoped filter chains: swagger chain (`@Order(0)`, prod only, Basic auth on swagger paths) and permit-all catch-all. `API_CHAIN_ORDER = 100` is reserved for a future JWT Bearer chain scoping authenticated endpoints (no `/api` path prefix exists — API versioning is via the `X-API-Version` header, see `WebMvcConfig.kt`). Also defines `CorsConfigurationSource` (security-level CORS, from `CorsProperties`) |
| `CorsProperties.kt` | `@ConfigurationProperties("cors")` + `@Validated`. Bound from `CORS_ALLOWED_ORIGINS` |
| `OpenApiConfig.kt` | Swagger metadata: title "뽀또 API", response envelope contract, common error code table |
| `WebMvcConfig.kt` | `WebMvcConfigurer.configureApiVersioning`: `X-API-Version` request header, default version `1` (Spring Framework 7 native API versioning) |
| `GcsProperties.kt` | `@ConfigurationProperties("gcs")` + `@Validated`. `bucket`, `credentialsPath`, `signedUrlExpirationMinutes`, `timeoutMillis` — bound from `GCS_*` env vars |
| `GcsConfig.kt` | Defines the `Storage` bean. Authenticates by reading the service account key file (`gcsProperties.credentialsPath`) via `ServiceAccountCredentials.fromStream` — not the `GOOGLE_APPLICATION_CREDENTIALS` ambient method. Sets connect/read timeout (`gcsProperties.timeoutMillis`) via `HttpTransportOptions` so a slow/hanging GCS call can't block indefinitely |

## Rules

- New `XxxProperties` classes follow the CorsProperties pattern: data class, constructor binding, `@Validated` + jakarta constraints.
- When adding the JWT chain, insert it as a new bean at `API_CHAIN_ORDER`; do not modify the existing chains.
- 401/403 from the security filter chain are not wrapped in `ApiResponse`; align them via AuthenticationEntryPoint/AccessDeniedHandler when JWT lands.
- Don't mark beans that read local files/external credentials (e.g. `GcsConfig.storage`) `@Lazy`. Every integration test in this repo (extending `IntegrationTest`) boots the **entire** application context via `@SpringBootTest`, so instead of scattering `@Lazy` through production code to dodge test startup, provide a real, parseable dummy credentials file in tests (`src/test/resources/dummy-gcs-key.json`) so the bean can be created eagerly as normal. Building the `Storage` bean and V4-signing are local credential parsing / RSA signing operations with no network call, so a dummy key is enough. Follow this same pattern (keep the real bean as-is, add a dummy test fixture) for any new bean that reads local files or external credentials.

Update this file when configuration beans change.
