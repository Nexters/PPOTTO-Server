<!-- Parent: ../AGENTS.md -->

# identifier

`@JvmInline value class` typed identifiers (`UserId`, `BoardId`, `StickerId`, `AnalysisId`, `PhotoId`, `DrawingId`, `TermId`) wrapping `UUID`, each overriding `toString()` for log interpolation.

## Rules

- Typed ids flow through domain models, repository public signatures, application services, cross-domain ports, adapters, presentation (DTO id fields, `@PathVariable`, `@AuthenticatedUser`/`@CurrentUser` handler parameters), and the jOOQ layer itself: codegen `forcedType` converters (`global/jooq/`) generate id columns as typed fields, so DSL bindings compare typed ids directly and column-level transposition fails to compile.
- Raw `UUID` survives only at boundaries: the SecurityContext principal (`CurrentUserArgumentResolver` resolves it into the handler parameter, typed or raw), external representations (JWT subject, Redis keys) inside their adapters, raw-SQL binds (advisory locks), and UUID-surfaced internals (analysis pipeline), which wrap ids at DSL binding points instead of unwrapping.
- JSON and OpenAPI documents keep the unwrapped uuid-string representation. Jackson serializes value classes unwrapped; springdoc unwraps them in generic positions, and direct DTO id properties pin naming with `@get:JsonProperty` + `@get:Schema` because the JVM-mangled getter otherwise leaks into the schema.
- Handler methods with value class parameters get JVM-mangled names, so every operation on a converted `XxxApi` pins `@Operation(operationId = ...)` to keep the OpenAPI contract stable.
- Add a new id type only when the id crosses a method boundary; purely internal ids stay `UUID`.
