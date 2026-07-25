<!-- Parent: ../../../../../../AGENTS.md -->

# Tests

Kotest BehaviorSpec (Given-When-Then) on JUnit Platform, with Testcontainers for integration tests.

| File | Description |
|------|-------------|
| `ProjectConfig.kt` | Kotest global config; registers `SpringExtension` (required — base-class registration does not work in Kotest 6) |
| `support/IntegrationTest.kt` | Base class: `@SpringBootTest` + `@ActiveProfiles("test")` + Testcontainers import. Extend this for integration tests |
| `support/TestcontainersConfiguration.kt` | `@ServiceConnection` PostgreSQLContainer (pgvector/pgvector:pg18, matches compose image) |
| `ApplicationIntegrationTest.kt` | Context + DB round-trip smoke test |
| `../../../../../resources/application-test.yml` | Supplies values for env placeholders in tests (tests do not read `.env`) |

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
