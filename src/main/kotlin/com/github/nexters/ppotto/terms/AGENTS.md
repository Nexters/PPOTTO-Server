<!-- Parent: ../AGENTS.md -->

# terms

Terms domain. Owns effective term versions and append-only user agreement history.

| Directory | Description |
|-----------|-------------|
| `application/` | `TermsService` fluent anonymous/authenticated reads, required-term guard, idempotent agreement, and withdrawn-user agreement deletion pipelines |
| `domain/` | Pure term and agreement models plus `TERM-*` error codes |
| `infrastructure/` | Fluent jOOQ repositories for effective term lookup, idempotent agreement persistence, and user-scoped agreement deletion |
| `infrastructure/integration/WithdrawnUserTermAgreementDeletionAdapter.kt` | User-domain `WithdrawnUserTermAgreementDeletionPort` adapter through `TermsService` |
| `presentation/TermsApi.kt` | Version 1 terms HTTP mapping and Swagger contract |
| `presentation/TermsController.kt` | Fluent public optional-auth term lookup and protected agreement implementation |
| `presentation/TermsApiExamples.kt` | `ApiExampleProvider` implementation. Defines logged-in/anonymous terms list responses, the agreement request, and the `TERM-001` failure example as real DTO instances |
| `presentation/dto/` | Swagger-described terms request and response schemas |

Current versions are selected by the latest `effective_at` at or before the lookup time for each code. Anonymous current-term reads return every `agreed` value as `false`; authenticated reads project stored agreement state. Agreement writes never access the user repository and rely on the database foreign key for user integrity.

Because `term_agreements.user_id` is a real foreign key, withdrawn-user cleanup must delete agreements before the user row is hard-deleted. `terms` rows themselves are shared master data and are never deleted by that batch.

`Term.id`, `TermResult.id`, presentation (handler parameters, `TermResponse.id`, `AgreeTermsRequest.termIds`), and all service/repository user references use the typed `TermId`/`UserId` from `global/identifier`; jOOQ id columns are generated typed (codegen `forcedType`), so repository bindings pass typed ids straight through; raw `UUID` survives only on `TermAgreement.id` (internal record identity that never crosses a method boundary).

Update this file when the terms package layout changes.
