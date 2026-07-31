<!-- Parent: ../AGENTS.md -->

# terms

Terms domain. Owns effective term versions and append-only user agreement history.

| Directory | Description |
|-----------|-------------|
| `application/` | `TermsService` fluent anonymous/authenticated current terms, pending terms, required validation, and idempotent agreement pipelines |
| `domain/` | Pure term and agreement models plus `TERM-*` error codes |
| `infrastructure/` | jOOQ repositories for effective term lookup and idempotent agreement persistence |
| `presentation/TermsApi.kt` | Version 1 terms HTTP mapping and Swagger contract |
| `presentation/TermsController.kt` | Public optional-auth term lookup and protected agreement implementation |
| `presentation/dto/` | Swagger-described terms request and response schemas |

Current versions are selected by the latest `effective_at` at or before the lookup time for each code. Anonymous current-term reads return every `agreed` value as `false`; authenticated reads project stored agreement state. Agreement writes never access the user repository and rely on the database foreign key for user integrity.

Update this file when the terms package layout changes.
