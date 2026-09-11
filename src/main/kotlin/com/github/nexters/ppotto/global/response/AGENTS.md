<!-- Parent: ../AGENTS.md -->

# global.web

API response envelope.

| File | Description |
|------|-------------|
| `ApiResponse.kt` | `{success, data, error}` envelope, Swagger-described so every generated `ApiResponseXxx` schema documents the wrapper; `required` comes from `KotlinRequiredModelConverter`. Controllers return `ApiResponse.success(data)`; failures are produced by `GlobalExceptionHandler` |

## Rules

- Every controller endpoint returns `ApiResponse<T>`. No raw bodies.

Update this file when the response contract changes.
