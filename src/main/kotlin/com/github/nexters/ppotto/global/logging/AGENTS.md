<!-- Parent: ../AGENTS.md -->

# global.logging

Request logging.

| File | Description |
|------|-------------|
| `RequestLoggingFilter.kt` | `OncePerRequestFilter`. Uses one expression-bodied request-id and elapsed-time chain, logs `METHOD uri status elapsed`, and skips `/actuator/**` |

Update this file when logging behavior changes.
