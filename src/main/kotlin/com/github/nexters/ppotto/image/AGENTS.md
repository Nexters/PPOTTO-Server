<!-- Parent: ../AGENTS.md -->

# image

Image domain. `Board : Image = 1:N` via `images.board_id`, no DB-level FK constraint (same reasoning as `board/`). Backs the upcoming GCP presigned-url upload flow (separate PR) — this PR only sets up the entity.

| Directory | Description |
|-----------|---------|
| `domain/Image.kt` | Pure Kotlin model: `id`, `boardId`, `uploadStatus`, `uploadSessionId`, `createdAt` |
| `domain/UploadStatus.kt` | `PENDING` / `COMPLETED` / `FAILED` — pure Kotlin enum, not the jOOQ-generated type |
| `infrastructure/ImageRepository.kt` | DSLContext-based persistence: `save(boardId, uploadSessionId)`, `findById()`, `findByBoardId()` |

## Rules

- No `presentation`/`application` layer yet — presigned URL issuance/callback (which will transition `upload_status`) lands in a later PR.
- `upload_status` is a DB `VARCHAR`, not a Postgres `ENUM` type — adding a new status later is a plain data change, no `ALTER TYPE` migration needed. Validation of the value set lives only in `UploadStatus`; the repository maps `String ↔ UploadStatus` (`UploadStatus.valueOf(...)` / `.name`) at the boundary.
- `board_id` has no FK constraint, same caveat as `board.user_id` in `board/AGENTS.md`.
- No `updated_at` on this table (unlike `user`/`board`) — only `created_at`. If `upload_status` transitions need to be timestamped later, add the column explicitly rather than assuming `created_at` reflects it.

Update this file when layers are added to this domain.
