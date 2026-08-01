<!-- Parent: ../AGENTS.md -->

# identifier

`@JvmInline value class` typed identifiers (`UserId`, `BoardId`, `StickerId`, `AnalysisId`, `PhotoId`, `DrawingId`, `TermId`) wrapping `UUID`, each overriding `toString()` for log interpolation.

## Rules

- Typed ids flow through domain models, repository public signatures, application services, cross-domain ports, and adapters.
- Raw `UUID` survives only at boundaries: jOOQ DSL bindings inside repositories (`.value` out, wrap in `toDomain`), presentation DTOs/path variables/security principal (controllers wrap), and external representations (JWT subject, Redis keys) inside their adapters.
- Add a new id type only when the id crosses a method boundary; purely internal ids stay `UUID`.
