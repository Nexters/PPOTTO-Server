<!-- Parent: ../AGENTS.md -->

# global.observability

Sentry SDK wiring that the Spring Boot starter cannot infer on its own. Everything else (DSN, environment, sampling rate, logback bridge levels) is declarative in `src/main/resources/config/sentry.yml`.

| File | Description |
|------|-------------|
| `SentryUserContextProvider.kt` | `SentryUserProvider` bean that copies the authenticated principal into the Sentry user context as `user.id`. The principal is the raw `UUID` that `BearerTokenAuthenticationFilter` puts into the `SecurityContext`, so no repository lookup happens on the event path. Sentry's own `SentryUserFilter` runs at `Ordered.LOWEST_PRECEDENCE`, i.e. after the Spring Security chain has populated the context |
| `SentryTracesSampler.kt` | `SentryOptions.TracesSamplerCallback` bean that drops `/actuator/**` transactions (health-check noise) by returning `0.0`. Every other request returns `null`, which makes Sentry fall back to the configured `sentry.traces-sample-rate`. Sampling on the request path instead of on transaction name avoids depending on how Sentry names transactions |

## Rules

- No email or other PII goes into the user context. `sentry.send-default-pii` stays `false`, and enriching the user with an email would mean a per-event cross-domain lookup, which `global` is not allowed to do anyway.
- Returning a non-null value from `SentryTracesSampler` overrides `sentry.traces-sample-rate` entirely. Return `null` for anything that should keep following the configured rate.
- Incoming `sentry-trace` / `baggage` headers are continued by the starter's `SentryTracingFilter`. Do not re-implement trace propagation here.

Update this file when Sentry beans are added or removed.
