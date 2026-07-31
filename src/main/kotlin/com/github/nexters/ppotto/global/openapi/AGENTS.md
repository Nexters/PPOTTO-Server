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
| `ApiExampleFactory.kt` | Serializes example objects with the application `ObjectMapper` bean and wraps them in swagger-model `Example`s. Named examples also fill `description` with the name, matching swagger-core behavior |
| `ApiExampleOperationCustomizer.kt` | springdoc `OperationCustomizer`. Injects registry examples into the requestBody and into each response code's media type |
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
