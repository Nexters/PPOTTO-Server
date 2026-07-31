<!-- Parent: ../AGENTS.md -->

# global.security

Shared MVC boundary that injects the authenticated HTTP user as a controller argument.

| File | Description |
|------|-------------|
| `AuthenticatedUser.kt` | Controller argument annotation that requires a UUID authenticated user |
| `CurrentUser.kt` | Argument annotation that optionally accepts a UUID authenticated user on public APIs |
| `CurrentUserArgumentResolver.kt` | Resolves the SecurityContext UUID principal with fluent branching and converts a missing required authentication into `COMMON-004` |

## Rules

- Only UUID principals are accepted; any other principal type is rejected with `COMMON-004`, even for optional authentication.
- Use `@AuthenticatedUser` for required authentication and `@CurrentUser` for optional authentication on public APIs.
- Resource ownership and domain permissions are validated in application services.

Update this file when authentication argument contracts change.
