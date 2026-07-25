<!-- Parent: ../AGENTS.md -->

# global.logging

Request logging.

| File | Description |
|------|-------------|
| `RequestLoggingFilter.kt` | `OncePerRequestFilter`. Puts an 8-char `requestId` into MDC, logs `METHOD uri status elapsed` per request, skips `/actuator/**`. MDC key appears in the console pattern configured in `config/logging.yml` |

Update this file when logging behavior changes.
