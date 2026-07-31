<!-- Parent: ../../../../../../AGENTS.md -->

# Tests

Kotest BehaviorSpec (Given-When-Then) on JUnit Platform, with Testcontainers for integration tests.

| File | Description |
|------|-------------|
| `ProjectConfig.kt` | Kotest global config; registers `SpringExtension` (required — base-class registration does not work in Kotest 6) |
| `support/IntegrationTest.kt` | Base class: `@SpringBootTest` + `@ActiveProfiles("test")` + Testcontainers import. Extend this for integration tests |
| `support/TestcontainersConfiguration.kt` | `@ServiceConnection` PostgreSQLContainer (pgvector/pgvector:pg18, matches compose image) |
| `ApplicationIntegrationTest.kt` | Context + DB round-trip smoke test |
| `analysis/` | Authenticated ownership, upload verification, active-analysis integration, persistence, and API contract tests |
| `user/` | User domain, concurrent signup, AES-GCM token cipher, repository, application-service, session-revoking withdrawal, cleanup, and controller tests |
| `auth/` | OAuth HTTP timeout/config validation, deterministic JWT tamper detection, active-user refresh validation, cross-domain 가입 rollback, and Bearer security tests including optional-auth terms behavior |
| `terms/application/TermsServiceTest.kt` | Current and pending term status, required validation, idempotent agreement, and schema-valid user fixture tests |
| `terms/infrastructure/TermRepositoryTest.kt` | Current effective term selection and idempotent agreement repository integration with schema-valid users |
| `global/security/CurrentUserArgumentResolverTest.kt` | Required and optional UUID principal resolution contract tests |
| `global/openapi/OpenApiDocumentationTest.kt` | Generated OpenAPI metadata, version header, schema description, error response, and authentication mode tests |
| `terms/presentation/TermsControllerTest.kt` | Public anonymous current-term lookup, authenticated agreement state, protected agreement submission, and schema-valid user fixture integration tests |
| `terms/support/TermsTestSecurityConfig.kt` | Test-only UUID authentication principal filter for MockMvc |
| `board/domain/DrawingTest.kt` | Drawing scope and sticker ownership invariant unit tests |
| `board/application/BoardCommandServiceTest.kt` | Board count, deletion guards, and cascade command integration tests |
| `board/application/BoardCommandConcurrencyTest.kt` | User-scoped create/delete serialization and max/last-board concurrency regression tests |
| `board/application/BoardQueryServiceTest.kt` | Owned board detail composition integration tests |
| `board/application/BoardLayoutServiceTest.kt` | Idempotent layout and atomic ownership validation integration tests |
| `board/application/BoardLayoutConcurrencyTest.kt` | Layout/delete serialization regression test preventing drawing reinsertion after board deletion |
| `board/application/BoardStickerIntegrationTest.kt` | Production analysis/sticker port wiring, active-analysis deletion guard, detail/layout mapping, and cascade integration tests |
| `board/infrastructure/BoardRepositoryTest.kt` | Board persistence and active lookup integration tests |
| `board/infrastructure/DrawingRepositoryTest.kt` | Drawing JSONB upsert and soft-delete integration tests |
| `board/infrastructure/BoardExternalPortFallbackConfigurationTest.kt` | Standalone missing-adapter fail-closed contract tests |
| `board/presentation/BoardControllerTest.kt` | Authenticated CRUD response and ownership integration tests |
| `board/support/BoardTestConfig.kt` | Primary fake analysis/sticker application ports for board integration tests |
| `board/support/BoardTestFixtures.kt` | UUIDv7 and sticker response fixtures |
| `sticker/` | Sticker aggregate unit tests plus repository, service, and API integration tests |
| `../../../../../resources/application-test.yml` | Supplies values for env placeholders, including local-only auth keys, provider-token keys, OAuth HTTP timeouts, Redis, and JWT settings, in tests (tests do not read `.env`) |

## Rules

- Integration tests extend `IntegrationTest` and use constructor injection:

```kotlin
class PhotoServiceTest(
    service: PhotoService,
) : IntegrationTest({
    Given("사진이 등록된 상태에서") {
        When("아이디로 조회하면") {
            Then("사진 정보를 반환한다") { ... }
        }
    }
})
```

- Test names in Korean. Use capitalized `Given`/`When`/`Then` (lowercase `when` needs backticks).
- Pure unit tests extend any Kotest spec directly without Spring.
- One Spring context (and one Postgres container) is shared across all integration tests via context caching; avoid `@MockkBean`-style context mutations unless necessary.

Update this file when test infrastructure changes.
