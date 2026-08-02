<!-- Parent: ../AGENTS.md -->

# global.response

API response envelope.

| File | Description |
|------|-------------|
| `ApiResponse.kt` | `{success, data, error}` envelope, Swagger-described so every generated `ApiResponseXxx` schema documents the wrapper; `required` comes from `KotlinRequiredModelConverter`. Controllers return `ApiResponse.success(data)`; failures are produced by `GlobalExceptionHandler` |
| `PageResponse.kt` | Offset pagination payload: `items`, `page`, `size`, `totalCount`, `hasNext` |

## Rules

- Every controller endpoint returns `ApiResponse<T>`. No raw bodies.
- Paginated lists use `PageResponse` inside the envelope: `ApiResponse<PageResponse<T>>`.

Update this file when the response contract changes.
