<!-- Parent: ../AGENTS.md -->

# global.oauth

| File | Description |
|------|-------------|
| `OAuthProvider.kt` | Closed set of social-login providers (`KAKAO`, `APPLE`). Used as-is in auth request DTOs (unknown value → 400), in `auth` login/profile models, and in the `user` account model; `UserRepository` maps it to the jOOQ `OauthProvider` DB enum |

## Rules

- One enum for both domains. `auth` and `user` used to keep identical copies plus two `when` converters; adding a provider then meant three edits. Add a constant here, and the compiler points at every `when` that must grow.
- Never serialize it by `name`/`ordinal` to another service or Kafka; that rule (CLAUDE.md 5.3) only starts mattering when a second consumer appears.

Update this file when the provider set changes.
