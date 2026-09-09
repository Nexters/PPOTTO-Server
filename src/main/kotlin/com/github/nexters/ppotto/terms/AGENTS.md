<!-- Parent: ../AGENTS.md -->

# terms

Terms domain. Owns effective term versions and append-only user agreement history.

| Directory | Description |
|-----------|-------------|
| `application/TermsService.kt` | Anonymous/authenticated current-term reads, required-term guard, idempotent agreement, and withdrawn-user agreement deletion |
| `application/TermResult.kt` | Application-layer term projection carrying the requesting user's agreement state |
| `domain/` | Pure `Term` model plus `TERM-*` error codes |
| `infrastructure/` | jOOQ repositories for effective term lookup, idempotent agreement persistence, and user-scoped agreement deletion |
| `infrastructure/integration/WithdrawnUserTermAgreementDeletionAdapter.kt` | User-domain `WithdrawnUserTermAgreementDeletionPort` adapter through `TermsService` |
| `presentation/TermsApi.kt` | Version 1 terms HTTP mapping and Swagger contract |
| `presentation/TermsController.kt` | Public optional-auth term lookup and protected agreement implementation |
| `presentation/TermsApiExamples.kt` | `ApiExampleProvider` implementation. Defines logged-in/anonymous terms list responses, the agreement request, and the `TERM-001` failure example as real DTO instances |
| `presentation/dto/` | Swagger-described terms request and response schemas |

Current versions are selected by the latest `effective_at` at or before the lookup time for each code. Anonymous current-term reads return every `agreed` value as `false`; authenticated reads project stored agreement state. Agreement writes never access the user repository and rely on the database foreign key for user integrity.

`agree` is the only transaction in this domain: it reads the current terms, reads existing agreements, and inserts, and those three must see one snapshot. `deleteAgreements` is a single DELETE and opens no transaction.

There is no `TermAgreement` domain model. `saveAll` is an `ON CONFLICT DO NOTHING` insert that returns the number of newly stored rows, which is all any caller ever needed; reading agreements back goes through `findAgreedTermIds`.

Because `term_agreements.user_id` is a real foreign key, withdrawn-user cleanup must delete agreements before the user row is hard-deleted. `terms` rows themselves are shared master data and are never deleted by that batch.

`Term.id`, `TermResult.id`, presentation (handler parameters, `TermResponse.id`, `AgreeTermsRequest.termIds`), and all service/repository user references use the typed `TermId`/`UserId` from `global/identifier`; jOOQ id columns are generated typed (codegen `forcedType`), so repository bindings pass typed ids straight through.

Tests build users with the shared `support/UserTestFixtures.kt:saveTestUser()` and terms with `terms/support/TermsTestFixtures.kt:saveTerm()`, and authenticate through `SecurityMockMvcRequestPostProcessors.authentication(...)` like every other controller test. There is no terms-specific test security filter.

Update this file when the terms package layout changes.
