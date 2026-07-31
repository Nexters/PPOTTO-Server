<!-- Parent: ../AGENTS.md -->

# global.error

Error contract and global exception handling.

| File | Description |
|------|-------------|
| `ErrorCode.kt` | Interface: `status`, `code`, `message`. Domain error enums implement this |
| `CommonErrorCode.kt` | `COMMON-000` ~ `COMMON-006` (500, 400, 404, 405, 401, 403, 409) |
| `BusinessException.kt` | Base exception + semantic subclasses: `InvalidInputException`, `UnauthorizedException`, `ForbiddenException`, `NotFoundException`, `ConflictException` |
| `ErrorResponse.kt` | Error payload: `code`, `message`, `fieldErrors`, `timestamp`. Swagger-described with per-field examples so `ApiErrorResponse` renders real error codes and messages |
| `GlobalExceptionHandler.kt` | `@RestControllerAdvice`. Chained status-based logging and response pipeline wraps everything in `ApiResponse.error`. 4xx logged as warn, 5xx as error |

## Rules

- Domain errors: define `<Domain>ErrorCode` enum in the domain's `domain/` package with codes like `PHOTO-001`, then throw `NotFoundException(PhotoErrorCode.PHOTO_NOT_FOUND)` etc.
- Add new framework exception mappings to `GlobalExceptionHandler`, not to controllers.
- Keep code numbering stable; never reuse a retired code.

Update this file when the error contract changes.
