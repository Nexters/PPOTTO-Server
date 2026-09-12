<!-- Parent: ../AGENTS.md -->

# global.web

HTTP 계층의 공용 어휘 — 성공 응답 봉투(`ApiResponse`), API 버전 정책(`ApiVersions`), 인증 없이 열린 경로 목록(`PublicPaths`). 셋 다 스프링 설정이 아니라 설정이 읽는 정책이라 `global/config`가 아니라 여기에 둔다. `PublicPaths`는 시큐리티 체인과 각 필터의 `shouldNotFilter`, 그리고 Sentry 샘플러·요청 로깅 필터가 **같은 목록을 읽어야 하므로** 한 곳에만 존재해야 한다 — 경로 배열을 두 곳에 복사하지 말 것.

| File | Description |
|------|-------------|
| `ApiResponse.kt` | `{success, data, error}` envelope, Swagger-described so every generated `ApiResponseXxx` schema documents the wrapper; `required` comes from `KotlinRequiredModelConverter`. Controllers return `ApiResponse.success(data)`; failures are produced by `GlobalExceptionHandler` |

## Rules

- Every controller endpoint returns `ApiResponse<T>`. No raw bodies.

Update this file when the response contract changes.

## Files

| File | Responsibility |
|------|-------------|
| `PublicPaths.kt` | Single source of truth for public paths. Exposes ant patterns (`PUBLIC_API_PATTERNS`, `DOCUMENT_PATTERNS`) for the security chains and exact/prefix matchers (`isPublicApi`, `isDocument`) for `BearerTokenAuthenticationFilter.shouldNotFilter`, so the chain and the filter can never drift apart. `OPTIONAL_AUTH_GET_PATTERNS` covers the separate optional-auth case: those GETs are permitted anonymously by the chain but still pass through the Bearer filter, so a supplied token is resolved and the handler can tell owner from stranger. There is deliberately no `isOptionalAuthGet` predicate — the filter must *not* skip those paths, so nothing would call it; `PublicPathsTest` pins that pattern-versus-predicate agreement instead |
| `ApiVersions.kt` | `object`. Single source of truth for the API version policy: the header name (`API_VERSION_HEADER`), the server default (`DEFAULT_API_VERSION`), `SUPPORTED_API_VERSIONS`, the declared `@RequestMapping(version = ...)` of a handler type (`declaredVersionOf`), whether it is pinned rather than baseline (`isVersionPinned`), and which versions it accepts (`acceptedVersionsOf`). `WebMvcConfig`, `OpenApiConfig`, and `ApiVersioningTest` all read it, so the runtime config, the docs, and the regression guard can never disagree with the mappings |
| `ApiResponse.kt` | 성공 응답 봉투. 실패 봉투는 `global/error`의 `GlobalExceptionHandler`가 만든다 |

## Rules

- `GET /terms` and `GET /stickers/shared/{shareToken}` are the only anonymous-accessible endpoints, and they stay out of `isPublicApi` on purpose: `shouldNotFilter` would skip Bearer authentication entirely, so a supplied-but-invalid token would slip past instead of failing. `POST /terms/agreements` and every sticker endpoint including `GET /stickers/{stickerId}` remain protected. `PublicPathsTest` fails if a predicate and its ant pattern ever disagree, so adding a path means adding it to both.
- **Every mapping that does not change between versions declares a baseline version (`version = "1+"`), never a fixed one.** Spring detects supported versions from the mappings, so the moment one mapping declares `"2"` the whole application accepts version 2 — and a mapping fixed at `"1"` then answers a v2 request with `NotAcceptableApiVersionException` (400). Only an endpoint that has a v2 replacement at the same path and method may stay fixed at `"1"`. `ApiVersioningTest` fails when a fixed mapping has no sibling accepting every supported version, so a new endpoint pinned to `"1"` cannot ship.
- `VersionRequestCondition.getMatchingCondition` compares baselines even for a fixed version; the fixed-version rejection happens later in `handleMatch`. Never assert reachability with `getMatchingCondition` — use `acceptedVersionsOf`.
