<!-- Parent: ../AGENTS.md -->

# board

Board domain. `User : Board = 1:N`, and `Board : Drawing = 1:N`. Cross-domain referential integrity is enforced through application services and ports instead of direct repository access.

| Directory | Description |
|-----------|---------|
| `domain/Board.kt` | Pure Kotlin board model and board count/name policies |
| `domain/Drawing.kt` | Pure Kotlin drawing and creation models |
| `domain/DrawingScope.kt` | Drawing ownership scope: `STICKER` or `BOARD` |
| `domain/BoardErrorCode.kt` | `BOARD-001` through `BOARD-005` API errors |
| `infrastructure/BoardRepository.kt` | Active-board CRUD, ownership filtering, row locking, user-scoped command advisory locking, and soft deletion |
| `infrastructure/DrawingRepository.kt` | JSONB drawing upsert, lookup, ownership filtering, and board/sticker-scoped soft deletion |
| `infrastructure/StickerDrawingCommandAdapter.kt` | Sticker-domain drawing deletion port adapter backed by the board application service |
| `infrastructure/BoardExternalPortFallbackConfiguration.kt` | Fail-closed standalone fallbacks that back off when integration adapters are registered |
| `application/BoardCommandService.kt` | Default/create/rename/delete transaction boundaries and board policies |
| `application/BoardAccessService.kt` | Port-free board existence and ownership lookup boundary for cross-domain services |
| `application/BoardDrawingCommandService.kt` | Board-owned drawing soft-deletion transaction surface |
| `application/BoardQueryService.kt` | Board list/detail composition through the sticker query port |
| `application/BoardLayoutService.kt` | User-serialized sticker/drawing ownership validation and atomic changed-layout persistence |
| `application/port/BoardAnalysisActivityPort.kt` | Active-analysis check contract for safe board deletion |
| `application/port/BoardStickerPorts.kt` | Sticker query, ownership validation, layout, and cascade-deletion contracts |
| `presentation/BoardApi.kt` | `/boards` v1 CRUD mapping and Swagger contract |
| `presentation/BoardController.kt` | Board CRUD API implementation with request binding and required UUID user injection |
| `presentation/BoardLayoutApi.kt` | `PATCH /boards/{boardId}/layout` v1 mapping and Swagger contract |
| `presentation/BoardLayoutController.kt` | Board layout API implementation with request binding and required UUID user injection |
| `presentation/dto/BoardRequests.kt` | Swagger-described create and rename validation DTOs |
| `presentation/dto/BoardResponses.kt` | Swagger-described board list/detail, sticker, and drawing response DTOs |
| `presentation/dto/BoardLayoutRequest.kt` | Swagger-described nested sticker and drawing layout request DTOs |

## Rules

- Same DB-generated column convention as `user/`: board `id`/`createdAt`/`updatedAt` come back via `RETURNING`, never set client-side.
- Drawing IDs are client-generated UUIDv7 values and are upserted for retry idempotency.
- Soft-deleted boards and drawings never appear in active queries.
- `user_id` has no FK constraint. Application services validate ownership.
- Board create, layout update, and delete acquire the same user-scoped transaction advisory lock before validation and mutation.
- Missing analysis or sticker integration adapters always fail closed, including empty sticker query and command requests.
- Drawing `scope=STICKER` requires `stickerId`; `scope=BOARD` forbids it.
- Cross-domain board ownership checks use `BoardAccessService`, which must stay independent of external ports.
- Board code never reads analysis or sticker repositories directly.

Update this file when layers are added to this domain.
