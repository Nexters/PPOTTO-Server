<!-- Parent: ../AGENTS.md -->

# global.config

Spring configuration beans.

| File | Description |
|------|-------------|
| `SecurityConfig.kt` | Two scoped filter chains: swagger chain (`@Order(0)`, prod only, Basic auth on swagger paths) and permit-all catch-all. `API_CHAIN_ORDER = 100` is reserved for a future JWT Bearer chain on `/api/**`. Also defines `CorsConfigurationSource` (security-level CORS, from `CorsProperties`) |
| `CorsProperties.kt` | `@ConfigurationProperties("cors")` + `@Validated`. Bound from `CORS_ALLOWED_ORIGINS` |
| `OpenApiConfig.kt` | Swagger metadata: title "뽀또 API", response envelope contract, common error code table |

## Rules

- New `XxxProperties` classes follow the CorsProperties pattern: data class, constructor binding, `@Validated` + jakarta constraints.
- When adding the JWT chain, insert it as a new bean at `API_CHAIN_ORDER`; do not modify the existing chains.
- 401/403 from the security filter chain are not wrapped in `ApiResponse`; align them via AuthenticationEntryPoint/AccessDeniedHandler when JWT lands.

Update this file when configuration beans change.
