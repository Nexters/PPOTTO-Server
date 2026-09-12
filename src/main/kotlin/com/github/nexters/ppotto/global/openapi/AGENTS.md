<!-- Parent: ../AGENTS.md -->

# global.openapi

Shared Swagger response contracts and the foundation for injecting type-safe examples from code.

| File | Description |
|------|-------------|
| `ApiErrorResponses.kt` | Composed annotations for the 400 (`COMMON-001`) and 409 (`COMMON-006`) common error responses and the 200 success response whose `data` is always null. They declare only response codes, descriptions, and schemas — no examples |
| `ApiErrorResponse.kt` | Documentation-only failure envelope schema (`success`, `data`, `error`). Real failure responses are built by `GlobalExceptionHandler` via `ApiResponse.error` |
| `ApiExample.kt` | A single example (`ApiExample`: name, summary, real DTO instance) and the per-operation bundle (`OperationExamples`: request example plus per-response-code examples) |
| `ApiExampleProvider.kt` | Contribution interface implemented by each domain. Exposes a map of `KFunction` (API interface method reference) → `OperationExamples` |
| `ApiExampleRegistry.kt` | Collects every `ApiExampleProvider` into a map keyed by `KFunction.javaMethod`, and resolves a `HandlerMethod` back to its API interface method via `ClassUtils.getInterfaceMethodIfPossible`. Duplicate registrations for the same function fail fast at startup instead of silently overwriting each other |
| `KotlinRequiredModelConverter.kt` | swagger-core `ModelConverter`. Marks non-nullable Kotlin properties of project (`com.github.nexters.ppotto`) schemas as `required`, honoring `@JsonProperty` renames, so DTOs never need `requiredMode` annotations. Nullable properties stay optional |
| `ApiExampleFactory.kt` | Serializes example objects with the application `ObjectMapper` bean and wraps them in swagger-model `Example`s. Named examples also fill `description` with the name, matching swagger-core behavior |
| `ApiExampleOperationCustomizer.kt` | springdoc `OperationCustomizer`. Injects registry examples into the requestBody and into each response code's media type. Media types come out as `application/json` because `config/springdoc.yml` sets `default-produces-media-type` |
| `ApiExamples.kt` | Domain-agnostic examples: empty success response, `COMMON-001` (with field errors), `COMMON-004`, `COMMON-006`. The `error`/`errorExample` helpers that keep failure examples short also live here |

## Rules

- Endpoint summaries and domain-specific descriptions belong on each domain's `presentation/XxxApi.kt` interface. Controllers carry no Swagger annotations.
- Define examples as real request/response DTO instances, not `@ExampleObject` JSON strings. DTO changes become compile errors, and serialization goes through the production `ObjectMapper`, so production settings such as `default-property-inclusion: non_null` apply to examples too (null fields are omitted from examples as well).
- Domain examples live in that domain's `presentation/XxxApiExamples.kt` as `@Component ... : ApiExampleProvider`. Only examples shared with the same meaning across multiple domains belong in this package's `ApiExamples`.
- The mapping key is the API interface method reference (`AuthApi::login`), not `operationId`. springdoc's `operationId` is unstable when method names collide — it becomes order-dependent suffixes like `create_1`.
- Prefer `@Schema(example = ...)` for field-level examples. Use `ApiExampleProvider` only for envelope-level examples (whole request body, whole response).
- 401 responses are injected uniformly by `OpenApiConfig.operationCustomizer` with `ApiExamples.UNAUTHORIZED`, so do not redeclare them on individual APIs.
- Every new operation must also be registered with an `ApiExampleProvider`. Omissions are caught by `OpenApiExampleWiringTest`.

Update this file when shared OpenAPI annotations or the example injection mechanism changes.
- **예시의 코드와 메시지는 `ErrorCode` enum 에서만 온다.** `errorExample(errorCode, summary)` 가 `code`·`message` 를 enum 에서 읽으므로 예시에 문자열을 다시 적을 자리가 없다. 이전 시그니처는 둘을 손으로 받았고, 그래서 상한을 바꾸면 런타임은 새 숫자로 답하고 발행된 Swagger 예시는 옛 숫자를 보여주는 상태가 됐다 — 잡아주는 테스트가 없었다(`OpenApiExampleWiringTest` 는 예시의 *존재*만 본다).
- **예외는 다른 도메인의 에러 코드 하나뿐이다.** `crossDomainErrorExample(code, summary, message)` 는 도메인 경계가 상대 도메인의 `ErrorCode` import 를 금지하기 때문에 존재한다 — `analysis` 엔드포인트가 `BoardAccessService` 를 호출하므로 `BOARD-002` 를 반환할 수 있지만, `board.domain` 을 import 하면 `BoardAnalysisDependencyTest` 가 실패한다. 함수 이름이 그 복사가 의도된 것임을 표시하므로 `errorExample` 을 쓸 수 있는 곳에서는 쓰지 말 것.

