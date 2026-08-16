<!-- Parent: ../AGENTS.md -->

# global.logging

Request logging.

| File | Description |
|------|-------------|
| `RequestLoggingFilter.kt` | `OncePerRequestFilter` at `@Order(Ordered.HIGHEST_PRECEDENCE)` so it runs before the security filter chain and 401/403 rejections are still logged with a request id. Uses one expression-bodied request-id and elapsed-time chain, logs `METHOD uri status elapsed headers`, masks `Authorization` case-insensitively, and skips `/actuator/**` |

Update this file when logging behavior changes.
