<!-- Parent: ../AGENTS.md -->

# sticker

Sticker and recap domain. A sticker is the aggregate root; `StickerPhoto` and `RecapComment` belong to one sticker.

| Directory | Description |
|-----------|---------|
| `domain/` | Pure Kotlin `Sticker` aggregate with fluent state transitions and validation, creation values, `StickerPhoto`, `RecapComment`, type, and error code |
| `domain/Sticker.kt` | Aggregate root. `title` (제목 뱃지, max 15) and `summary` (리캡 한 줄 요약, max 100) are separate concepts validated by `validateTitle` / `validateSummary` |
| `domain/RecapComment.kt` | Recap comment model and creation value. `posX`/`posY` must both be null or both set; that pair is the only thing distinguishing a floating speech bubble from a bottom keyword chip |
| `infrastructure/StickerRepository.kt` | Fluent jOOQ persistence for sticker roots, analysis-result locking, ownership validation, root lookups, and board-scoped deletion targets including soft-deleted rows |
| `infrastructure/StickerCommandRepository.kt` | Field-specific atomic updates that never overwrite `deleted_at` from stale aggregate state, plus withdrawal hard deletion by id |
| `infrastructure/StickerRecapRepository.kt` | Fluent jOOQ batch persistence and deletion for recap photo links and comments |
| `infrastructure/BoardStickerAdapter.kt` | Expression-bodied board query/command port mappings backed by sticker application services |
| `infrastructure/GcsStickerImageStorage.kt` | Read and deletion side of generated sticker images: production `StickerImageStoragePort` adapter delegating `stickers.image_key` read URL signing to the shared `global/storage/GcsReadUrlIssuer` and object deletion to `global/storage/ObjectStorageCleaner`. It does not upload — `analysis`'s `GcsStickerStorage` writes the object |
| `infrastructure/integration/WithdrawnUserStickerDeletionAdapter.kt` | User-domain `WithdrawnUserStickerDeletionPort` adapter through `StickerWithdrawalService` |
| `application/` | Fluent transactional analysis-result save plus sticker query, command, and withdrawal pipelines |
| `application/port/` | Cross-domain contracts for analysis/photo ownership plus recap photo metadata and signed URLs |
| `presentation/StickerApi.kt` | Version 1 sticker and recap mapping and Swagger contract |
| `presentation/StickerController.kt` | Fluent Sticker API implementation with request binding and required UUID user injection |
| `presentation/StickerApiExamples.kt` | `ApiExampleProvider` 구현. 리캡 상세(한 줄 요약, 말풍선 3개, 키워드 칩 9개), 제목 수정 요청/응답, `STICKER-001` 실패 예시를 실제 DTO 인스턴스로 정의합니다 |
| `presentation/dto/` | Swagger-described sticker and recap request and response schemas |
| `presentation/StickerNotFoundApiResponse.kt` | 네 개 스티커 엔드포인트가 공유하는 404 `STICKER-001` 합성 어노테이션 |

## Rules

- `Sticker` is soft-deleted through `stickers.deleted_at`.
- A recap has exactly one one-line summary and it lives on `stickers.summary`, not in `recap_comments`. It is LLM-written only — no API updates it, so `Sticker.summary` is a read-only `val` while `title` stays mutable through `rename`. `GET /stickers/{id}` returns it as the top-level `summary` field, outside the `sticker` object, because the board sticker list has no use for it.
- `recap_comments` has no type column. The two kinds are told apart by coordinates alone: `pos_x`/`pos_y` set means a speech bubble floating around the sticker, both null means a bottom `테마 분석` keyword chip. `chk_recap_comment_position` enforces that the pair is all-or-nothing and `RecapCommentCreation` rejects a half-filled pair before any write. Never reintroduce a boolean flag for this — it duplicates the coordinates and lets the two disagree.
- Recap responses carry every photo with no pagination. Clients derive the displayed photo count from the array length, so no count field is stored or returned.
- `sticker_photos` and `recap_comments` intentionally have no `deleted_at` in the final ERD. Deleting a sticker hard-deletes these child rows in the same transaction so recap content is no longer retained as active application data.
- Deleting stickers also removes `STICKER`-scoped drawings through exactly one `StickerDrawingCommandPort` adapter in the same transaction. Absence or duplication fails before any sticker mutation; the drawing owner provides the production adapter.
- Sticker code never accesses the analysis, photo, or board repositories directly. Board ownership is checked through the port-free `BoardAccessService`; analysis/photo ownership and recap photo metadata come through the application ports, whose adapters call the `analysis` application services.
- `AnalysisPhotoOwnershipPort` must have exactly one adapter before analysis-result saving is enabled. The adapter must match `userId`, `boardId`, `analysisId`, and every source/recap photo id; absence, duplication, or mismatch fails before the first sticker write. The production adapter is `analysis`'s `StickerAnalysisPhotoOwnershipAdapter`, backed by `AnalysisQueryService.ownsAnalysisPhotos`.
- An analysis owns at most six stickers for its lifetime, including soft-deleted stickers. Repeating or concurrently retrying analysis-result saving keeps and returns the first committed result instead of appending another result.
- `RecapPhotoQueryPort` and `StickerImageStoragePort` are integration contracts with exactly one adapter each: `analysis`'s `StickerRecapPhotoAdapter` (through `PhotoQueryService`) and this domain's `GcsStickerImageStorage` (through `global/storage/`). The sticker application still fails fast if an adapter omits requested media, so a partial photo or image-URL result is a 500, never a silently short response.
- `StickerImageStoragePort` owns both read-URL issuing and object deletion because both target the same `stickers.image_key` objects and must stay on one adapter.
- Withdrawal deletion is the only path that hard-deletes stickers and ignores `deleted_at`; it deletes the sticker image objects first, then the recap children and sticker rows in one transaction. It runs before the analysis, photo, and board domains delete their rows because `stickers` has real foreign keys into all three.
- Every port here is collected as `List<Port>` and resolved with `singleOrNull()`, so tests must not register additional `@Primary` fakes for them — an extra bean is a duplicate, not an override, and it breaks the context instead of replacing the adapter. Integration tests exercise the production adapters directly.
- All recap media URLs are read-only V4 signed URLs that expire after `gcs.read-signed-url-expiration-minutes` (1 hour per the client contract), so clients re-fetch instead of caching them.
- Sticker image objects are written by `analysis` and read here; the two never overlap. `analysis`'s `GcsStickerStorage` uploads the PNG and `analysis`'s `StickerObjectKeys` owns the key convention (`stickers/{analysisId}/{themeIndex}-{sourcePhotoId}.png`); this domain only signs GET URLs for that key. `stickers.image_key` must therefore hold the bare object key, never the `gs://{bucket}/...` URI that `GcsStickerStorage.upload` returns — `GcsReadUrlIssuer` passes the stored value straight to `BlobId.of(bucket, key)`, so a `gs://` prefix would be signed as part of the object name and yield a URL that 404s.
- Controllers receive the authenticated UUID through `@AuthenticatedUser`; the shared resolver returns `COMMON-004` before controller execution when it is absent.
- `getByBoardId`, `validateOwnedByBoard`, `updateLayouts`, and `deleteAllByBoardId` are the board-domain integration surface. The board domain validates board ownership before calling these board-scoped operations.

Update this file when layers or integration contracts change.
