<!-- Parent: ../AGENTS.md -->

# global.error

Error contract and global exception handling.

| File | Description |
|------|-------------|
| `ErrorCode.kt` | Interface: `status`, `code`, `message`. Domain error enums implement this |
| `CommonErrorCode.kt` | `COMMON-000` ~ `COMMON-008` (500, 400, 404, 405, 401, 403, 409, 415, 406) |
| `BusinessException.kt` | Base exception + semantic subclasses: `InvalidInputException`, `UnauthorizedException`, `ForbiddenException`, `NotFoundException`, `ConflictException` |
| `ErrorResponse.kt` | Error payload: `code`, `message`, `fieldErrors`, `timestamp`. Swagger-described with per-field examples so `ApiErrorResponse` renders real error codes and messages |
| `GlobalExceptionHandler.kt` | `@RestControllerAdvice`. One `respond(status, error, e)` helper logs and wraps everything in `ApiResponse.error`. 5xx is logged as error with the stack trace; 4xx is logged as warn with the exception type and message only, because a client contract violation is not a defect and its stack trace is noise. Framework exceptions keep their own status: 415/406 map to `COMMON-007`/`COMMON-008`, and `ErrorResponseException` (including `ResponseStatusException`) preserves its status code with the closest `CommonErrorCode` envelope instead of degrading to 500 |

## Rules

- Domain errors: define `<Domain>ErrorCode` enum in the domain's `domain/` package with codes like `PHOTO-001`, then throw `NotFoundException(PhotoErrorCode.PHOTO_NOT_FOUND)` etc.
- Add new framework exception mappings to `GlobalExceptionHandler`, not to controllers.
- Keep code numbering stable; never reuse a retired code.

Update this file when the error contract changes.
