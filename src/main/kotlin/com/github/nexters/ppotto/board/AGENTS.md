<!-- Parent: ../AGENTS.md -->

# board

Board domain. `User : Board = 1:N` via `boards.user_id`, kept as a plain column with no DB-level FK constraint (by design, to avoid migration friction) — referential integrity is an application-level concern.

| Directory | Description |
|-----------|---------|
| `domain/Board.kt` | Pure Kotlin model: `id`, `userId`, `createdAt`, `updatedAt` |
| `infrastructure/BoardRepository.kt` | DSLContext-based persistence: `save(userId)`, `findById()`, `findByUserId()` |
| `application/BoardQueryService.kt` | `getById(id)` — throws `NotFoundException` if not found. The `analysis` domain accesses this only through this Service when validating board existence (cross-domain access must never go through the Repository directly) |

## Rules

- No `presentation` layer yet.
- Same DB-generated column convention as `user/`: `id`/`createdAt`/`updatedAt` come back via `RETURNING`, never set client-side.
- `user_id` has no FK constraint — do not assume the DB will reject an orphaned `user_id`; `BoardQueryService.getById` is responsible for existence validation.

Update this file when layers are added to this domain.
