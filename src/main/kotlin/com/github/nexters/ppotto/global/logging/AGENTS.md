<!-- Parent: ../AGENTS.md -->

# global.logging

Request logging.

| File | Description |
|------|-------------|
| `RequestLoggingFilter.kt` | `OncePerRequestFilter`. Uses a fluent elapsed-time scope, puts an 8-char `requestId` into MDC, logs `METHOD uri status elapsed` per request, and skips `/actuator/**` |

Update this file when logging behavior changes.
