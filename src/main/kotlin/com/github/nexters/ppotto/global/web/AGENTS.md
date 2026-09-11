<!-- Parent: ../AGENTS.md -->

# global.web

HTTP 계층의 공용 어휘 — 성공 응답 봉투(`ApiResponse`), API 버전 정책(`ApiVersions`), 인증 없이 열린 경로 목록(`PublicPaths`). 셋 다 스프링 설정이 아니라 설정이 읽는 정책이라 `global/config`가 아니라 여기에 둔다. `PublicPaths`는 시큐리티 체인과 각 필터의 `shouldNotFilter`, 그리고 Sentry 샘플러·요청 로깅 필터가 **같은 목록을 읽어야 하므로** 한 곳에만 존재해야 한다 — 경로 배열을 두 곳에 복사하지 말 것.

| File | Description |
|------|-------------|
| `ApiResponse.kt` | `{success, data, error}` envelope, Swagger-described so every generated `ApiResponseXxx` schema documents the wrapper; `required` comes from `KotlinRequiredModelConverter`. Controllers return `ApiResponse.success(data)`; failures are produced by `GlobalExceptionHandler` |

## Rules

- Every controller endpoint returns `ApiResponse<T>`. No raw bodies.

Update this file when the response contract changes.
