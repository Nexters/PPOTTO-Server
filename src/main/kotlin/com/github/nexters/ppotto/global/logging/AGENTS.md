<!-- Parent: ../AGENTS.md -->

# global.logging

Request logging.

| File | Description |
|------|-------------|
| `RequestLoggingFilter.kt` | `OncePerRequestFilter` at `@Order(Ordered.HIGHEST_PRECEDENCE)` so it runs before the security filter chain and 401/403 rejections are still logged with a request id. Puts the request id in MDC, logs `METHOD uri status elapsed headers` in a `finally`, and skips `/actuator/**` via `PublicPaths.isActuator` |

## Rules

- Header masking is **not** decided here. `HttpPayloadAttributes.isSensitiveHeader` owns the sensitive-header set and this filter calls it, so the request log and the Sentry span attributes can never mask different headers. Adding a header to mask means editing that one set, never this file.

Update this file when logging behavior changes.
