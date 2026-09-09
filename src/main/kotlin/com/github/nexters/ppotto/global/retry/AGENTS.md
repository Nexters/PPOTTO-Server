<!-- Parent: ../AGENTS.md -->

# global.retry

| File | Description |
|------|-------------|
| `RetryPolicy.kt` | `RetryPolicy(maxAttempts, initialDelayMillis, backoffMultiplier)` and `retrying(policy, log, description, sleep, retryOn) { }`. Returns `Result<T>`, so the caller sets the failure policy: a push adapter does `getOrDefault(emptyList())`, an external API call does `getOrThrow()`. Every attempt after the first logs at WARN with the delay, the final failure at ERROR |

## Rules

- `sleep` is a parameter so tests inject a recorder instead of waiting; production leaves the default `Thread.sleep`.
- `retryOn` decides which failures are worth another attempt (Pixian retries only 5xx and transport errors). The default retries everything.
- This is the higher-order-function form of the cross-cutting concern (CLAUDE.md 2.2): it stays a function until a third caller with the same shape appears.

Update this file when the retry contract changes.
