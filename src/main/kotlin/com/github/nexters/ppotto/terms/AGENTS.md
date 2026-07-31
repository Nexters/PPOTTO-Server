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
| `presentation/dto/` | Swagger-described terms request and response schemas plus `TermsApiExamples` (로그인/비로그인 약관 목록 응답, 동의 요청, `TERM-001` 실패 예시 JSON 상수) |

Current versions are selected by the latest `effective_at` at or before the lookup time for each code. Anonymous current-term reads return every `agreed` value as `false`; authenticated reads project stored agreement state. Agreement writes never access the user repository and rely on the database foreign key for user integrity.

Because `term_agreements.user_id` is a real foreign key, withdrawn-user cleanup must delete agreements before the user row is hard-deleted. `terms` rows themselves are shared master data and are never deleted by that batch.

Update this file when the terms package layout changes.
