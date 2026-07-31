<!-- Parent: ../AGENTS.md -->

# sticker

Sticker and recap domain. A sticker is the aggregate root; `StickerPhoto` and `RecapComment` belong to one sticker.

| Directory | Description |
|-----------|---------|
| `domain/` | Pure Kotlin `Sticker` aggregate, creation values, `StickerPhoto`, `RecapComment`, type, and error code |
| `infrastructure/StickerRepository.kt` | jOOQ persistence for sticker roots, analysis-result locking, and root lookups |
| `infrastructure/StickerCommandRepository.kt` | Field-specific atomic updates that never overwrite `deleted_at` from stale aggregate state |
| `infrastructure/StickerRecapRepository.kt` | jOOQ persistence for recap photo links and comments |
| `infrastructure/BoardStickerAdapter.kt` | Explicit board query/command port mappings backed by sticker application services |
| `application/` | Fluent transactional analysis-result save plus sticker query and command pipelines |
| `application/port/` | Cross-domain contracts for analysis/photo ownership plus recap photo metadata and signed URLs |
| `presentation/StickerApi.kt` | Version 1 sticker and recap mapping and Swagger contract |
| `presentation/StickerController.kt` | Sticker API implementation with request binding and required UUID user injection |
| `presentation/dto/` | Swagger-described sticker and recap request and response schemas |

## Rules

- `Sticker` is soft-deleted through `stickers.deleted_at`.
- `sticker_photos` and `recap_comments` intentionally have no `deleted_at` in the final ERD. Deleting a sticker hard-deletes these child rows in the same transaction so recap content is no longer retained as active application data.
- Deleting stickers also removes `STICKER`-scoped drawings through exactly one `StickerDrawingCommandPort` adapter in the same transaction. Absence or duplication fails before any sticker mutation; the drawing owner provides the production adapter.
- Sticker code never accesses the analysis, photo, or board repositories directly. Board ownership is checked through the port-free `BoardAccessService`; recap photo metadata and read URLs come through the application port.
- `AnalysisPhotoOwnershipPort` must have exactly one adapter before analysis-result saving is enabled. The adapter must match `userId`, `boardId`, `analysisId`, and every source/recap photo id; absence, duplication, or mismatch fails before the first sticker write. The analysis/photo owner provides the production adapter after those application services are available.
- An analysis owns at most six stickers for its lifetime, including soft-deleted stickers. Repeating or concurrently retrying analysis-result saving keeps and returns the first committed result instead of appending another result.
- `RecapPhotoQueryPort` and `StickerImageQueryPort` are integration contracts. The photo/GCS owners must provide exactly one adapter for media-bearing recap queries; the sticker application fails fast if an adapter omits requested media.
- Controllers receive the authenticated UUID through `@AuthenticatedUser`; the shared resolver returns `COMMON-004` before controller execution when it is absent.
- `getByBoardId`, `validateOwnedByBoard`, `updateLayouts`, and `deleteAllByBoardId` are the board-domain integration surface. The board domain validates board ownership before calling these board-scoped operations.

Update this file when layers or integration contracts change.
