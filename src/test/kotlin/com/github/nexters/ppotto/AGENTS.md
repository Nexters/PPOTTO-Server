<!-- Parent: ../../../../../../AGENTS.md -->

# Tests

Kotest BehaviorSpec (Given-When-Then) on JUnit Platform, with Testcontainers for integration tests.

| File | Description |
|------|-------------|
| `ProjectConfig.kt` | Kotest global config; registers `SpringExtension` (required — base-class registration does not work in Kotest 6) |
| `support/IntegrationTest.kt` | Base class: `@SpringBootTest` + `@ActiveProfiles("test")` + Testcontainers and object-storage test imports. Extend this for integration tests |
| `support/TestcontainersConfiguration.kt` | `@ServiceConnection` PostgreSQLContainer (pgvector/pgvector:pg18, matches compose image) |
| `support/ObjectStorageTestConfiguration.kt` | `@Primary` `RecordingObjectStorageCleaner` imported by every integration test so no test can ever delete a real GCS object; records deleted prefixes and keys for assertions |
| `support/DatabaseCleaner.kt` | Truncates every application table in foreign-key-safe order (recap, sticker photo, drawing, sticker, photo, analysis, board, term agreement, term, user) before each spec |
| `support/UserJourneyTestConfig.kt` | `@Primary` stub Kakao `OAuthClient` and in-memory `RefreshTokenStore` wired into a `@Primary` `AuthService`, so `/auth/login` runs end to end with real JWTs and no provider HTTP or Redis |
| `ApplicationIntegrationTest.kt` | Context + DB round-trip smoke test |
| `UserJourneyIntegrationTest.kt` | Issue #50 whole-journey integration test. Drives login, terms agreement, board creation, analysis-result save, recap read, sticker edit/delete, withdrawal, and the cleanup batch through MockMvc in one run |
| `analysis/` | Authenticated ownership, upload verification, active-analysis/status lookup, progress/result persistence, Gemini-shaped classification save mapping, and API contract tests |
| `analysis/infrastructure/integration/AnalysisStickerIntegrationTest.kt` | Production analysis/sticker port wiring, analysis-result save to recap read round trip with real signed URLs, and photo-ownership rejection tests |
| `user/` | User domain, concurrent signup, AES-GCM token cipher, repository, application-service, session-revoking withdrawal, cleanup, and controller tests |
| `user/infrastructure/integration/WithdrawnUserDataDeletionAdapterTest.kt` | Production `WithdrawnUserDataDeletionPort` wiring (real composite adapter, not the fail-closed fallback), full cross-domain hard deletion with object-storage prefixes and keys, and batch idempotency |
| `user/infrastructure/WithdrawnUserCleanupSchedulerTest.kt` | Disabled-by-default scheduler registration, retention/cron default validity, and retention cutoff behaviour for a just-withdrawn user |
| `auth/` | OAuth HTTP timeout/config validation, deterministic JWT tamper detection, active-user refresh validation, Bearer security tests including optional-auth terms behavior, and cross-domain 가입 tests: 기본 보드 실패 rollback, 약관 조회 실패 rollback, Apple authorization code 교환 실패 rollback, 로그인 트랜잭션 경계, 가입 전용 트랜잭션 bean timeout 배선, 동시 가입 중복 방지 |
| `terms/application/TermsServiceTest.kt` | Current and pending term status, required validation, idempotent agreement, and schema-valid user fixture tests |
| `terms/infrastructure/TermRepositoryTest.kt` | Current effective term selection and idempotent agreement repository integration with schema-valid users |
| `global/security/CurrentUserArgumentResolverTest.kt` | Required and optional UUID principal resolution contract tests |
| `global/error/GlobalExceptionHandlerIntegrationTest.kt` | Framework exception status preservation: unsupported Content-Type returns 415 with `COMMON-007`, unsupported Accept returns 406 instead of degrading to 500 |
| `global/openapi/OpenApiDocumentationTest.kt` | `/v3/api-docs` 실제 출력 검증: 메타데이터, 인터페이스에 선언한 엔드포인트 계약, operation마다 `X-API-Version` 파라미터가 정확히 1개인지, 성공 응답의 봉투 스키마와 상황별 예시가 함께 노출되는지, 도메인 에러 코드 실패 예시, 예시 값이 운영 `ObjectMapper` 설정(null 생략, `Instant` UTC 표기, `Double` 표기) 그대로인지, 스키마 필드 이름·예시, 인증 모드 |
| `global/openapi/OpenApiExampleWiringTest.kt` | 예시 배선 누락 회귀 테스트. `RequestMappingHandlerMapping`이 노출하는 모든 애플리케이션 핸들러 메서드가 `ApiExampleRegistry`에 등록되어 있는지, 등록 수와 매칭 수가 같은지(고아 항목 없음), 모든 핸들러가 응답 예시를 가지는지, 그리고 `/v3/api-docs`에서 본문이 있는 모든 응답에 예시가 하나 이상 실려 나가는지 확인합니다. 예시가 실제 DTO 인스턴스라서 필드 계약은 컴파일러가 잡고, 이 테스트는 컴파일러가 볼 수 없는 배선만 봅니다 |
| `terms/presentation/TermsControllerTest.kt` | Public anonymous current-term lookup, authenticated agreement state, protected agreement submission, and schema-valid user fixture integration tests |
| `terms/support/TermsTestSecurityConfig.kt` | Test-only UUID authentication principal filter for MockMvc |
| `board/BoardAnalysisDependencyTest.kt` | Source-level cross-domain contract: board never imports analysis types, analysis only imports the board application boundary and port |
| `board/domain/DrawingTest.kt` | Drawing scope and sticker ownership invariant unit tests |
| `board/application/BoardAccessServiceTest.kt` | `MANDATORY` row-locking lookup contract: fails outside a transaction, joins the caller transaction, and hides other users' boards |
| `board/application/BoardCommandServiceTest.kt` | Board count, deletion guards, and cascade command integration tests |
| `board/application/BoardCommandConcurrencyTest.kt` | User-scoped create/delete serialization and max/last-board concurrency regression tests |
| `board/application/BoardQueryServiceTest.kt` | Owned board detail composition integration tests |
| `board/application/BoardLayoutServiceTest.kt` | Idempotent layout and atomic ownership validation integration tests |
| `board/application/BoardLayoutConcurrencyTest.kt` | Layout/delete serialization regression test preventing drawing reinsertion after board deletion |
| `board/application/BoardStickerIntegrationTest.kt` | Production analysis/sticker port wiring, active-analysis deletion guard, detail/layout mapping, and cascade integration tests |
| `board/application/BoardAnalysisContractTest.kt` | Real `BoardAnalysisActivityAdapter` injection, per-status deletion guard, `uk_analysis_active` uniqueness/key-column/status alignment, and last-board/ownership precedence integration tests |
| `board/application/BoardAnalysisDeletionConcurrencyTest.kt` | Both delete/analysis-create interleavings: create after delete yields `BOARD-002`, delete after create yields `BOARD-005`. The blocking sticker port resets its latches in `beforeTest` so every leaf gets fresh ones |
| `board/infrastructure/BoardRepositoryTest.kt` | Board persistence and active lookup integration tests |
| `board/infrastructure/DrawingRepositoryTest.kt` | Drawing JSONB upsert and soft-delete integration tests |
| `board/infrastructure/BoardExternalPortFallbackConfigurationTest.kt` | Standalone missing-adapter fail-closed contract tests |
| `board/presentation/BoardControllerTest.kt` | Authenticated CRUD response and ownership integration tests |
| `board/support/BoardTestConfig.kt` | Primary fake analysis/sticker application ports for board integration tests |
| `board/support/BoardTestFixtures.kt` | UUIDv7, `NewDrawing` builder, and sticker response fixtures |
| `sticker/support/StickerTestFixtures.kt` | Shared `StickerCreation`/`StickerLayout` fixtures (`textStickerCreation`, `defaultStickerLayout`) reused across sticker, board, and analysis integration tests |
| `sticker/` | Sticker aggregate unit tests plus repository, service, and API integration tests. Covers the recap one-line summary and the coordinate-only split between floating speech bubbles and bottom keyword chips. No port fakes live here: the analysis/photo ownership, recap photo, and sticker image ports are `List<Port>` + `singleOrNull()` contracts, so a fake bean would duplicate the production adapter instead of overriding it |
| `../../../../../resources/application-test.yml` | Supplies values for env placeholders, including local-only auth keys, provider-token keys, OAuth HTTP timeouts, Redis, JWT, GCS signed URL expirations, and Vertex AI settings, in tests (tests do not read `.env`) |

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
- `IntegrationTest` uses `IsolationMode.InstancePerLeaf` while the Spring context stays cached, so stateful test doubles (latches, counters) must be resettable and reset per test. One-shot singleton fields silently stop blocking once a spec gains a second leaf.
- `@Primary` only overrides single-bean injection. For a port injected as `List<Port>` and resolved with `singleOrNull()` (all `sticker/application/port` contracts), registering a fake adds a second bean and breaks the service — test the production adapter instead.
- GCS signing needs no network: `src/test/resources/dummy-gcs-key.json` is a real RSA key, so read/upload V4 signed URLs are asserted against `https://storage.googleapis.com/ppotto-test-bucket/...` with real signatures. Only object listing/upload verification needs `analysis/support/AnalysisTestConfig`.
- Object deletion never reaches GCS either: `IntegrationTest` always imports `support/ObjectStorageTestConfiguration`, whose `@Primary` `RecordingObjectStorageCleaner` replaces `GcsObjectStorageCleaner` for every context. Do not remove that import when adding a test that triggers withdrawal cleanup.
- `UserJourneyIntegrationTest` authenticates through the real `BearerTokenAuthenticationFilter` with the JWT returned by `POST /auth/login`, so it exercises the HTTP layer instead of injecting a principal. The filter's servlet auto-registration is disabled; in the test profile it runs because the permit-all fallback chain in `SecurityConfig` adds it explicitly. Analysis start goes through MockMvc and the test follows the pipeline-saved sticker result instead of calling `AnalysisResultSaveService` directly.

Update this file when test infrastructure changes.
