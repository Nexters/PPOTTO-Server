<!-- Parent: ../AGENTS.md -->

# image

Image domain. `Board : Image = 1:N` via `images.board_id`, no DB-level FK constraint (same reasoning as `board/`). Both signed URL issuance and the upload-completion callback (`upload_status` transition) are implemented.

| Directory | Description |
|-----------|---------|
| `domain/Image.kt` | Pure Kotlin model: `id`, `boardId`, `uploadStatus`, `uploadSessionId`, `originalFileName`, `createdAt` |
| `domain/UploadStatus.kt` | `PENDING` / `COMPLETED` / `FAILED` — pure Kotlin enum, not the jOOQ-generated type |
| `domain/ImageUploadUrlIssuer.kt` | Port interface for signed URL issuance. No Spring/GCS SDK imports — the adapter is `infrastructure/GcsImageUploadUrlIssuer.kt` |
| `domain/ImageErrorCode.kt` | `IMAGE-xxx` error codes (`UNSUPPORTED_FILE_EXTENSION`, `ALREADY_PROCESSED_UPLOAD`) |
| `infrastructure/ImageRepository.kt` | DSLContext-based persistence: `save(boardId, uploadSessionId, originalFileName)`, `findById()`, `findByBoardId()`, `updateStatus(id, expectedStatus, newStatus)` — optimistic conditional UPDATE (`WHERE upload_status = expectedStatus`), returns `null` if no match |
| `infrastructure/GcsImageUploadUrlIssuer.kt` | GCS SDK implementation of `ImageUploadUrlIssuer`. Injects `Storage`/`GcsProperties` to issue V4 PUT signed URLs |
| `application/ImageUploadService.kt` | `issueUploadUrls`: validates the board exists → validates the extension whitelist → generates a shared `uploadSessionId` for the batch → saves Images + issues signed URLs. `completeUpload`: validates the Image belongs to the requested boardId → conditional `PENDING → COMPLETED/FAILED` transition, throws `ConflictException` if already processed |
| `application/ImageUploadUrlsResult.kt` | Result type returned by the service (kept separate from the presentation dto) |
| `presentation/ImageUploadController.kt` | `POST /api/v1/boards/{boardId}/images/signed-urls`, `PATCH /api/v1/boards/{boardId}/images/{imageId}/upload-status` |
| `presentation/dto/` | `IssueImageUploadUrlsRequest`, `IssueImageUploadUrlsResponse`, `CompleteImageUploadRequest` (`result`: a separate enum allowing only `COMPLETED`/`FAILED`, designed so a request can never revert status back to `PENDING`), `CompleteImageUploadResponse` |

## Rules

- The upload-completion callback works by the client uploading directly to GCS via the signed URL and then **explicitly reporting** completion — there's no GCS event webhook or server-side polling/reconciliation job. If the client abandons the flow without reporting, that Image stays `PENDING` forever (known limitation; a reconciliation job is out of scope for this PR).
- `updateStatus` only allows the transition when the current status is `PENDING` — reporting again on an Image that's already `COMPLETED`/`FAILED` is rejected with `ImageErrorCode.ALREADY_PROCESSED_UPLOAD` (409). `boardId` ownership validation and the status transition are both handled inside the same `@Transactional` method (`completeUpload`).
- `upload_status` is a DB `VARCHAR`, not a Postgres `ENUM` — adding a new status is a plain data change, no `ALTER TYPE` needed. Value-set validation lives only in `UploadStatus`; the repository maps `String ↔ UploadStatus` at the boundary (`UploadStatus.valueOf(...)` / `.name`).
- `board_id` has no FK constraint, same caveat as in `board/AGENTS.md`.
- `original_file_name` stores the client-provided original filename as-is — used for extension/Content-Type inference and retry matching. The object key is deterministically built as `images/{imageId}/{sanitizedFileName}`; sanitizing (stripping path-traversal characters) happens in `ImageUploadService` and isn't stored in a separate column.
- Any new test that actually invokes `ImageUploadService` (`.issueUploadUrls(...)`, `.completeUpload(...)`) must `@Import` `image/support/ImageUploadTestConfig` so the `@Primary` fake replaces the real GCS adapter. `ImageUploadService` is a plain `@Service`, so it's instantiated in every integration test context that extends `IntegrationTest` (`@SpringBootTest`) — including tests that don't import the fake (e.g. `BoardRepositoryTest`). Those need the real `GcsImageUploadUrlIssuer` → `Storage` bean to construct successfully, which is why `src/test/resources/dummy-gcs-key.json` must exist. See `global/config/AGENTS.md` for the mechanism.
- The file-count limit is enforced with `@Size(max = 200)` on `IssueImageUploadUrlsRequest.fileNames`.

Update this file when layers are added to this domain.
