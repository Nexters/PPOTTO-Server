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
| `application/port/BoardStickerPorts.kt` | Sticker query, ownership validation, layout, and cascade-deletion contracts |
| `presentation/BoardApi.kt` | `/boards` v1 CRUD mapping and Swagger contract |
| `presentation/BoardController.kt` | Fluent Board CRUD API implementation with request binding and required UUID user injection |
| `presentation/BoardLayoutApi.kt` | `PATCH /boards/{boardId}/layout` v1 mapping and Swagger contract |
| `presentation/BoardLayoutController.kt` | Fluent Board layout API implementation with request binding and required UUID user injection |
| `presentation/dto/BoardRequests.kt` | Swagger-described create and rename validation DTOs |
| `presentation/dto/BoardResponses.kt` | Swagger-described board list/detail, sticker, and drawing response DTOs |
| `presentation/dto/BoardLayoutRequest.kt` | Swagger-described nested sticker and drawing layout request DTOs |
| `presentation/BoardApiExamples.kt` | `ApiExampleProvider` 구현. 보드 목록/상세/생성/이름 변경 응답, 편집 모드별 layout 요청, `BOARD-001`~`BOARD-005` 실패 예시를 실제 DTO 인스턴스로 정의합니다. `BoardApi`와 `BoardLayoutApi` 예시를 함께 담습니다 |
| `presentation/BoardNotFoundApiResponse.kt` | 네 개 보드 엔드포인트가 공유하는 404 `BOARD-002` 합성 어노테이션 |

## Rules

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
- Cross-domain board ownership checks use `BoardAccessService`, which must stay independent of external ports.
- Board code never reads analysis or sticker repositories directly.
- Withdrawal hard deletion is the only path that ignores `deleted_at`; every other query stays soft-delete aware. It runs after the sticker domain has removed its rows, because `stickers.board_id` is a real FK.
- `getDetail` must not be wrapped in `@Transactional`: the sticker query port signs V4 read URLs (local RSA, CPU-bound) and a surrounding transaction would pin a pooled connection through that work on the board-entry hot path. Ownership still resolves first through `BoardAccessService.getOwnedById` in its own read-only transaction, and READ_COMMITTED per-statement snapshots make a wrapping transaction add no consistency.

Update this file when layers are added to this domain.
