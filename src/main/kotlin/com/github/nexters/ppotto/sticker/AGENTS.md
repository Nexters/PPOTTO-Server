<!-- Parent: ../AGENTS.md -->

# sticker

Sticker and recap domain. A sticker is the aggregate root; `StickerPhoto` and `RecapComment` belong to one sticker.

| Directory | Description |
|-----------|---------|
| `domain/` | Pure Kotlin `Sticker` aggregate with fluent state transitions and validation, creation values, `StickerPhoto`, `RecapComment`, type, and error code |
| `infrastructure/StickerRepository.kt` | Fluent jOOQ persistence for sticker roots, analysis-result locking, ownership validation, and root lookups |
| `infrastructure/StickerCommandRepository.kt` | Field-specific atomic updates that never overwrite `deleted_at` from stale aggregate state |
| `infrastructure/StickerRecapRepository.kt` | Fluent jOOQ batch persistence and deletion for recap photo links and comments |
| `infrastructure/BoardStickerAdapter.kt` | Expression-bodied board query/command port mappings backed by sticker application services |
| `infrastructure/GcsStickerImageStorage.kt` | Production `StickerImageQueryPort` adapter delegating sticker `image_key` read URLs to the shared `global/storage/GcsReadUrlIssuer` |
| `application/` | Fluent transactional analysis-result save plus sticker query and command pipelines |
| `application/port/` | Cross-domain contracts for analysis/photo ownership plus recap photo metadata and signed URLs |
| `presentation/StickerApi.kt` | Version 1 sticker and recap mapping and Swagger contract |
| `presentation/StickerController.kt` | Fluent Sticker API implementation with request binding and required UUID user injection |
| `presentation/dto/` | Swagger-described sticker and recap request and response schemas |

## Rules

- `Sticker` is soft-deleted through `stickers.deleted_at`.
- `sticker_photos` and `recap_comments` intentionally have no `deleted_at` in the final ERD. Deleting a sticker hard-deletes these child rows in the same transaction so recap content is no longer retained as active application data.
- Deleting stickers also removes `STICKER`-scoped drawings through exactly one `StickerDrawingCommandPort` adapter in the same transaction. Absence or duplication fails before any sticker mutation; the drawing owner provides the production adapter.
- Sticker code never accesses the analysis, photo, or board repositories directly. Board ownership is checked through the port-free `BoardAccessService`; analysis/photo ownership and recap photo metadata come through the application ports, whose adapters call the `analysis` application services.
- `AnalysisPhotoOwnershipPort` must have exactly one adapter before analysis-result saving is enabled. The adapter must match `userId`, `boardId`, `analysisId`, and every source/recap photo id; absence, duplication, or mismatch fails before the first sticker write. The production adapter is `analysis`'s `StickerAnalysisPhotoOwnershipAdapter`, backed by `AnalysisQueryService.ownsAnalysisPhotos`.
- An analysis owns at most six stickers for its lifetime, including soft-deleted stickers. Repeating or concurrently retrying analysis-result saving keeps and returns the first committed result instead of appending another result.
- `RecapPhotoQueryPort` and `StickerImageQueryPort` are integration contracts with exactly one adapter each: `analysis`'s `StickerRecapPhotoAdapter` (through `PhotoQueryService`) and this domain's `GcsStickerImageStorage` (through `global/storage/GcsReadUrlIssuer`). The sticker application still fails fast if an adapter omits requested media, so a partial photo or image-URL result is a 500, never a silently short response.
- Every port here is collected as `List<Port>` and resolved with `singleOrNull()`, so tests must not register additional `@Primary` fakes for them — an extra bean is a duplicate, not an override, and it breaks the context instead of replacing the adapter. Integration tests exercise the production adapters directly.
- All recap media URLs are read-only V4 signed URLs that expire after `gcs.read-signed-url-expiration-minutes` (1 hour per the client contract), so clients re-fetch instead of caching them.
- Controllers receive the authenticated UUID through `@AuthenticatedUser`; the shared resolver returns `COMMON-004` before controller execution when it is absent.
- `getByBoardId`, `validateOwnedByBoard`, `updateLayouts`, and `deleteAllByBoardId` are the board-domain integration surface. The board domain validates board ownership before calling these board-scoped operations.

Update this file when layers or integration contracts change.
