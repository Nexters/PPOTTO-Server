<!-- Parent: ../AGENTS.md -->

# global.lock

| File | Description |
|------|-------------|
| `AdvisoryLock.kt` | `@AdvisoryLock(namespace, key)` — method annotation that serializes callers on a Postgres transaction-scoped advisory lock. `key` is SpEL over the method parameters (`#userId`, `#command.analysisId`); the lock string is `namespace:key` |
| `AdvisoryLockAspect.kt` | The one aspect for that one concern. Fails closed: `check`s an active transaction (an annotated method without `@Transactional` is a bug, 500), evaluates the key, runs `pg_advisory_xact_lock(hashtextextended(key, 0))`, then proceeds. `@Order(ADVISORY_LOCK_ADVICE_ORDER)` sits after `TRANSACTION_ADVICE_ORDER`, so the lock is always taken inside the transaction the method opened |

## Rules

- Lock policy: throw immediately, never swallow. A missing transaction or an unevaluable key aborts the call.
- The transaction advice order is pinned in `global/config/TransactionConfig.kt` (`@EnableTransactionManagement(order = TRANSACTION_ADVICE_ORDER)`). Boot's default is `LOWEST_PRECEDENCE`, which nothing can run inside of; the explicit order is what lets this aspect be the inner advice.
- Spring AOP proxies are per bean: a `@AdvisoryLock` method called from another method of the same class (self-invocation) is not intercepted. Annotate every public entry that needs the lock, or delegate through another bean.
- Kotlin value-class parameters (`UserId`, `AnalysisId`) arrive as their underlying `UUID` in the join point; `toString()` yields the uuid string, so keys stay stable across the value-class boundary.
- `javaParameters = true` in `build.gradle.kts` keeps parameter names in bytecode, which is what lets SpEL resolve `#userId`.

Update this file when the lock contract changes.
