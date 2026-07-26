<!-- Parent: ../AGENTS.md -->

# user

User domain. Minimal by design: no profile fields yet, only identity and timestamps.

| Directory | Description |
|-----------|---------|
| `domain/User.kt` | Pure Kotlin model: `id`, `createdAt`, `updatedAt` |
| `infrastructure/UserRepository.kt` | DSLContext-based persistence: `save()`, `findById()` |

## Rules

- No `presentation`/`application` layer yet — nothing calls this domain over HTTP. Add those when the first use case needing a User endpoint lands.
- `id`/`createdAt`/`updatedAt` are DB-generated (`uuidv7()` default, `now()` default, `set_updated_at()` trigger) — `save()` inserts default values and reads them back via `RETURNING`, never sets them from application code.

Update this file when layers are added to this domain.
