<!-- Parent: ../AGENTS.md -->

# board

Board domain. `User : Board = 1:N`, and `Board : Drawing = 1:N`. Cross-domain referential integrity is enforced through application services and ports instead of direct repository access.

| Directory | Description |
|-----------|---------|
| `domain/Board.kt` | Pure Kotlin board model and board count/name policies |
| `domain/Drawing.kt` | Pure Kotlin drawing models as a sealed union: `Drawing.Stroke` (free-form `stroke` JSON plus `strokeWidth`) and `Drawing.Text` (`content`, `fontSize`, centre `posX`/`posY`, `maxWidth`, `rotation`), mirrored by `NewDrawing.Stroke`/`NewDrawing.Text` for creation. `Drawing.Text.MAX_CONTENT_LENGTH` is the one place the 32-character limit lives; the request DTO's `@Size` reads it |
| `domain/DrawingType.kt` | Persisted discriminator for the drawing union: `STROKE` or `TEXT` |
| `domain/DrawingScope.kt` | Drawing ownership scope: `STICKER` or `BOARD` |
| `domain/BoardStickerType.kt` | Sticker form as this domain sees it: `IMAGE` or `TEXT`. Board owns the port contract, so it declares its own type instead of importing `sticker.domain.StickerType` — the adapter lives in sticker and referencing sticker types here would make board depend back on sticker and close the cycle. `BoardStickerAdapter` maps the two with an exhaustive `when`, so a new `StickerType` constant fails to compile until board decides how to expose it |
| `domain/BoardErrorCode.kt` | `BOARD-001` through `BOARD-005` API errors |
| `infrastructure/BoardRepository.kt` | Active-board CRUD, ownership filtering, row locking, user-scoped command advisory locking, and soft deletion |
| `infrastructure/DrawingRepository.kt` | Single-statement multi-row JSONB drawing upsert (`excluded`-based `ON CONFLICT` update guarded per row by `board_id`, one stroke serialization per drawing, results re-ordered to input order), guarded lookup, ownership filtering, board/sticker-scoped soft deletion, and board-scoped hard deletion |
| `infrastructure/BoardWithdrawalRepository.kt` | Withdrawn-user board id lookup and hard deletion, kept out of `BoardRepository` so the active-board repository stays soft-delete only |
| `infrastructure/StickerDrawingCommandAdapter.kt` | Sticker-domain drawing deletion port adapter backed by the board application service |
| `infrastructure/integration/WithdrawnUserBoardDeletionAdapter.kt` | User-domain `WithdrawnUserBoardDeletionPort` adapter through `BoardWithdrawalService` |
| `infrastructure/BoardExternalPortFallbackConfiguration.kt` | Fail-closed standalone fallbacks that back off when integration adapters are registered |
| `application/BoardCommandService.kt` | Fluent default/create/rename/delete transaction pipelines and board policies |
| `application/BoardAccessService.kt` | Port-free board existence, ownership, and `MANDATORY`-propagation row-locking ownership lookup boundary for cross-domain services |
| `application/BoardDrawingCommandService.kt` | Board-owned drawing soft-deletion transaction surface |
| `application/BoardWithdrawalService.kt` | Withdrawn-user board id lookup and atomic drawing/board hard deletion, including already soft-deleted rows |
| `application/BoardQueryService.kt` | Fluent board list/detail composition through the sticker query port. Cross-domain ownership lookups belong to `BoardAccessService`, not here. `getDetail` is intentionally non-transactional so the sticker port's signed-URL issuing never holds a DB connection |
| `application/BoardLayoutService.kt` | Expression-bodied user-serialized sticker/drawing guard chain and atomic changed-layout persistence |
| `application/port/BoardAnalysisActivityPort.kt` | Active-analysis check contract for safe board deletion |
| `application/port/BoardStickerPorts.kt` | Sticker query, ownership validation, layout, and cascade-deletion contracts. `BoardStickerItem.type` is `BoardStickerType`, so the swagger enum on the board response comes from a type rather than a hand-written `allowableValues` list |
| `presentation/BoardApi.kt` | `/boards` list/create/rename/delete mapping and Swagger contract. Baseline `version = "1+"` because none of it changed in v2 |
| `presentation/BoardDetailApi.kt` | `GET /boards/{boardId}` pinned to `version = "1"`, split out of `BoardApi` so only the detail response is frozen at v1 |
| `presentation/BoardDetailV2Api.kt` | `GET /boards/{boardId}` at `version = "2+"`, returning strokes and texts |
| `presentation/BoardLayoutV2Api.kt` | `PATCH /boards/{boardId}/layout` at `version = "2+"` |
| `presentation/BoardController.kt` | Fluent Board CRUD API implementation with request binding and required typed user injection |
| `presentation/BoardDetailController.kt` | v1 detail implementation |
| `presentation/BoardDetailV2Controller.kt` | v2 detail implementation |
| `presentation/BoardLayoutV2Controller.kt` | v2 layout implementation |
| `presentation/BoardLayoutApi.kt` | `PATCH /boards/{boardId}/layout` v1 mapping and Swagger contract |
| `presentation/BoardLayoutController.kt` | Fluent Board layout API implementation with request binding and required typed user injection |
| `presentation/dto/BoardRequests.kt` | Swagger-described create and rename validation DTOs |
| `presentation/dto/BoardResponses.kt` | Swagger-described **v1** board list/detail, sticker, and drawing response DTOs. Detail drops `Drawing.Text` and folds `zIndex` back into the `stroke` JSON |
| `presentation/dto/BoardV2Responses.kt` | Swagger-described **v2** detail response. `DrawingV2Response` is a `type`-discriminated union of `Stroke` and `Text` with `zIndex` as a real field |
| `presentation/dto/BoardLayoutV2Request.kt` | Swagger-described **v2** layout request. `DrawingCreateV2Request` is a `type`-discriminated union so jakarta validation runs per variant; colours are constrained to `#RRGGBB` here, which v1 never enforced |
| `presentation/dto/DrawingLegacyZIndex.kt` | The v1 `zIndex`-in-`stroke`-JSON shim: `legacyZIndex`, `withoutLegacyZIndex`, `withLegacyZIndex` |
| `presentation/dto/BoardLayoutRequest.kt` | Swagger-described nested sticker and drawing layout request DTOs |
| `presentation/BoardApiExamples.kt` | `ApiExampleProvider` implementation. Defines board list/detail/create/rename responses, per-edit-mode layout requests, and `BOARD-001`~`BOARD-005` failure examples as real DTO instances. Holds the examples for both `BoardApi` and `BoardLayoutApi` |
| `presentation/BoardNotFoundApiResponse.kt` | Composed annotation for the 404 `BOARD-002` response shared by the four board endpoints |

## Rules

- Typed identifiers (`BoardId`, `UserId`, `DrawingId`, `StickerId` from `global/identifier/`) flow end to end: domain models, repository public signatures, application services, the `application/port/` contracts, and presentation (`@AuthenticatedUser`/`@PathVariable` parameters and DTO id fields). jOOQ id columns are generated typed (codegen `forcedType`), so DSL bindings and `toDomain` pass typed ids straight through with no unwrapping; JSON stays unwrapped uuid strings via Jackson value class serialization, with `@get:JsonProperty` + `@get:Schema` pinning springdoc property naming on id fields.
- Same DB-generated column convention as `user/`: board `id`/`createdAt`/`updatedAt` come back via `RETURNING`, never set client-side.
- Drawing IDs are client-generated UUIDv7 values and are upserted for retry idempotency.
- Soft-deleted boards and drawings never appear in active queries.
- `user_id` has no FK constraint. Application services validate ownership.
- Board create, layout update, and delete acquire the same user-scoped transaction advisory lock before validation and mutation.
- Board deletion checks the last-board rule first, then the active-analysis rule, so a single board reports `BOARD-004` even while an analysis is running. Ownership is resolved before both, so a non-owner always gets `BOARD-002`.
- Deletion and analysis creation serialize on the target board row (`findOwnedByIdForUpdate`), so an active analysis can never be attached to a board that is being deleted. Board deletion takes that row lock before the active-analysis rule, so an analysis that commits first is always observed.
- `BoardAccessService.getOwnedByIdForUpdate` is `@Transactional(propagation = MANDATORY)`: a caller without an open transaction fails fast instead of committing away the row lock right after taking it. It must never be `readOnly = true` because Postgres rejects `SELECT ... FOR UPDATE` in a read-only transaction.
- Missing analysis or sticker integration adapters always fail closed, including empty sticker query and command requests. The production context must resolve `BoardAnalysisActivityPort` to `BoardAnalysisActivityAdapter`, never to the fallback.
- Drawing `scope=STICKER` requires `stickerId`; `scope=BOARD` forbids it.
- A drawing row is either a stroke or a text, and the DB enforces it: `chk_drawings_stroke_shape` and `chk_drawings_text_shape` tie each type to exactly the columns it must fill, the same way `chk_drawings_scope_sticker` ties scope to `sticker_id`. Adding a third type means a new check constraint, not a nullable column nobody validates.
- **A controller class carries exactly one type-level `@RequestMapping`.** Implementing two versioned API interfaces from one controller makes Spring apply one interface's version to every method and abort startup with `Ambiguous mapping`. One controller per API interface — that is why the detail and layout endpoints each have a v1 and a v2 controller.
- **`zIndex` moved from the `stroke` JSON into a real column, but the shipped v1 client still reads and writes it inside that JSON** (`board-drawing.ts` stuffs it there to share a stacking space with stickers). The v1 DTOs shim both directions through `DrawingLegacyZIndex.kt` and the JSON stored in the column never carries the key, so the two can never drift. v2 exposes `zIndex` directly. Deleting the shim breaks every deployed client.
- **The v1 detail response drops texts.** A v1 client parses `stroke.points` out of every drawing, so a text row would come back with no points and no `strokeWidth` and render as an empty path with a `NaN` hit radius. Texts are a v2-only feature, so v2 is where they appear.
- Colour format (`#RRGGBB`) is enforced on v2 requests only. v1 has always accepted any non-blank string and tightening it would reject requests the deployed client is entitled to make.
- Cross-domain board ownership checks use `BoardAccessService`, which must stay independent of external ports.
- Board code never reads analysis or sticker repositories directly.
- Withdrawal hard deletion is the only path that ignores `deleted_at`; every other query stays soft-delete aware. It runs after the sticker domain has removed its rows, because `stickers.board_id` is a real FK.
- `getDetail` must not be wrapped in `@Transactional`: the sticker query port signs V4 read URLs (local RSA, CPU-bound) and a surrounding transaction would pin a pooled connection through that work on the board-entry hot path. Ownership still resolves first through `BoardAccessService.getOwnedById` in its own read-only transaction, and READ_COMMITTED per-statement snapshots make a wrapping transaction add no consistency.

Update this file when layers are added to this domain.
