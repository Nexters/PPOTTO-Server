<!-- Parent: ../AGENTS.md -->

# global.security

Shared MVC boundary that injects the authenticated HTTP user as a controller argument.

| File | Description |
|------|-------------|
| `AuthenticatedUser.kt` | Controller argument annotation that requires an authenticated user |
| `CurrentUser.kt` | Argument annotation that optionally accepts an authenticated user on public APIs |
| `CurrentUserArgumentResolver.kt` | Resolves the SecurityContext UUID principal in a single `when` over the read-once principal and converts a missing required authentication into `COMMON-004` |

## Rules

- Only UUID principals are accepted; any other principal type is rejected with `COMMON-004`, even for optional authentication.
- The anonymous path is a real branch, not a theoretical one: the optional-auth chain hands the handler an `AnonymousAuthenticationToken`, and `@CurrentUser` must resolve it to `null` while `@AuthenticatedUser` raises `COMMON-004`. `CurrentUserArgumentResolverTest` covers both directions of `supportsParameter` too, so a resolver that always answers `true` cannot pass.
- Use `@AuthenticatedUser` for required authentication and `@CurrentUser` for optional authentication on public APIs.
- Handler parameters may be declared as `UserId`/`UserId?` or raw `UUID`/`UUID?`: value classes compile down to the underlying `UUID`, so the resolver's single `UUID` parameter-type check covers both and no wrapping code is needed.
- Resource ownership and domain permissions are validated in application services.

Update this file when authentication argument contracts change.
