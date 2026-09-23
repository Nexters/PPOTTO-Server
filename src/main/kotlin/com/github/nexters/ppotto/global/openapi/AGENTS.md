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
| `KotlinRequiredModelConverter.kt` | swagger-core `ModelConverter` that shapes project (`com.github.nexters.ppotto`) schemas for type generators such as openapi-typescript. (1) Marks non-nullable Kotlin properties as `required`, honoring `@JsonProperty` renames, so DTOs never need `requiredMode` annotations; nullable properties stay optional. (2) Restores value-class property names: a `val id: AnalysisId` without `@get:JsonProperty` would otherwise surface as the JVM-mangled getter name (`id-YTcRAVk`) and drop out of `required`. (3) Flattens `@JsonSubTypes` polymorphism: the parent keeps only `oneOf` + `discriminator.mapping` (no sibling `properties`), and each member loses its `allOf` parent reference and gains a constant `type` enum property, so the generated TypeScript is a plain discriminated union instead of `{…} & (A \| B)`. `OpenApiDocumentationTest` pins all three |
| `PolymorphicSchemaFlattener.kt` | The polymorphism half of the converter above, split out to keep the converter under detekt's function limit. Reads `@JsonSubTypes`/`@JsonTypeInfo` from the sealed parent and `@Schema(name)` from each member to build the `discriminator.mapping` |
| `ApiExampleFactory.kt` | Serializes example objects with the application `ObjectMapper` bean and wraps them in swagger-model `Example`s. Named examples also fill `description` with the name, matching swagger-core behavior |
| `ApiExampleOperationCustomizer.kt` | springdoc `OperationCustomizer`. Injects registry examples into the requestBody and into each response code's media type. Media types come out as `application/json` because `config/springdoc.yml` sets `default-produces-media-type` |
| `ApiExamples.kt` | Domain-agnostic examples: empty success response, `COMMON-001` (with field errors), `COMMON-004`, `COMMON-006`. The `error`/`errorExample` helpers that keep failure examples short also live here |

## Rules

- Endpoint summaries and domain-specific descriptions belong on each domain's `presentation/XxxApi.kt` interface. Controllers carry no Swagger annotations.
- Define examples as real request/response DTO instances, not `@ExampleObject` JSON strings. DTO changes become compile errors, and serialization goes through the production `ObjectMapper`, so production settings such as `default-property-inclusion: non_null` apply to examples too (null fields are omitted from examples as well).
- Domain examples live in that domain's `presentation/XxxApiExamples.kt` as `@Component ... : ApiExampleProvider`. Only examples shared with the same meaning across multiple domains belong in this package's `ApiExamples`.
- The mapping key is the API interface method reference (`AuthApi::login`), not `operationId`. springdoc's `operationId` is unstable when method names collide — it becomes order-dependent suffixes like `create_1`.
- Every `@Operation` must set `operationId` explicitly. Without it springdoc falls back to the JVM method name, and any method with a value-class parameter (`UserId`, `AnalysisId`, …) is name-mangled by the Kotlin compiler, so the document ends up with `create-5qyZsBA`. `OpenApiDocumentationTest` fails on any `operationId` containing `-`.
- Prefer `@Schema(example = ...)` for field-level examples. Use `ApiExampleProvider` only for envelope-level examples (whole request body, whole response).
- 401 responses are injected uniformly by `OpenApiConfig.operationCustomizer` with `ApiExamples.UNAUTHORIZED`, so do not redeclare them on individual APIs.
- Every new operation must also be registered with an `ApiExampleProvider`. Omissions are caught by `OpenApiExampleWiringTest`.
- Every response code an endpoint can actually return must be declared on the `XxxApi` interface (`@ApiResponse`) and given an example. openapi-typescript generates one response type per declared status code, so an undeclared 400/502 is invisible to the client. `OpenApiDocumentationTest` pins the codes that were missing before (regenerate 502, board delete 400, layout `STICKER-008`, share 400).

Update this file when shared OpenAPI annotations or the example injection mechanism changes.
- **예시의 코드와 메시지는 `ErrorCode` enum 에서만 온다.** `errorExample(errorCode, summary)` 가 `code`·`message` 를 enum 에서 읽으므로 예시에 문자열을 다시 적을 자리가 없다. 이전 시그니처는 둘을 손으로 받았고, 그래서 상한을 바꾸면 런타임은 새 숫자로 답하고 발행된 Swagger 예시는 옛 숫자를 보여주는 상태가 됐다 — 잡아주는 테스트가 없었다(`OpenApiExampleWiringTest` 는 예시의 *존재*만 본다).
- **예외는 다른 도메인의 에러 코드 하나뿐이다.** `crossDomainErrorExample(code, summary, message)` 는 도메인 경계가 상대 도메인의 `ErrorCode` import 를 금지하기 때문에 존재한다 — `analysis` 엔드포인트가 `BoardAccessService` 를 호출하므로 `BOARD-002` 를 반환할 수 있지만, `board.domain` 을 import 하면 `BoardAnalysisDependencyTest` 가 실패한다. 함수 이름이 그 복사가 의도된 것임을 표시하므로 `errorExample` 을 쓸 수 있는 곳에서는 쓰지 말 것.

