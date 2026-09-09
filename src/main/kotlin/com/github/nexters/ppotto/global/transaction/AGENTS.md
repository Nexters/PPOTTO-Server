<!-- Parent: ../AGENTS.md -->

# global.transaction

| File | Description |
|------|-------------|
| `AfterCommit.kt` | `afterCommit { }` — runs the block after the surrounding transaction commits, or immediately when no transaction synchronization is active. Post-commit side effects (Redis revoke, notifications, external calls) go through this instead of hand-written `TransactionSynchronization` objects, and the no-transaction fallback is built in as CLAUDE.md 2.1 requires |

Update this file when the helper changes.
