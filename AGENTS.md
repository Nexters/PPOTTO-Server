# ppotto Server

Backend server for ppotto (뽀또). Kotlin 2.3 / Spring Boot 4.1 / JDK 25 / PostgreSQL 18 / Flyway / jOOQ / Kotest. Single Gradle module.

## Commands

| Command | Description |
|---|---|
| `./gradlew build` | Compile + tests + ktlint + detekt. Must pass before finishing any task |
| `./gradlew ktlintFormat` | Auto-fix code style violations |
| `./gradlew flywayMigrate jooqCodegen` | Apply migrations + regenerate jOOQ code (needs local DB: `docker compose up -d`) |
| `./gradlew bootRun` | Run locally (reads `.env` via spring-dotenv) |
| `./gradlew koverHtmlReport` | Test coverage report (`build/reports/kover/html`) |

## Architecture (DDD-lite)

- One top-level package under `com.github.nexters.ppotto` = one domain. `global` is the shared module.
- Domain package layout:

```
photo/
├── presentation/    Controller, request/response dto
├── application/     Service (transaction boundary), QueryService
├── domain/          domain model, XxxErrorCode
└── infrastructure/  Repository (jOOQ DSLContext), external clients
```

- Dependency direction: presentation → application → domain ← infrastructure. `domain` must not import Spring or jOOQ.
- Cross-domain access goes through the other domain's application Service only. Never touch another domain's Repository or jOOQ tables directly.
- Reads: QueryService may project directly to dto with jOOQ. Writes go through the domain model.
- Never expose jOOQ-generated POJOs/Records in API responses. Always map to dto.

## Branch & PR Rules

- Branch: off `dev`, named `feat/이슈번호-기능간단설명` (e.g. `feat/1-user-board-image-entity`).
- PR target: `dev`, not `main`. `main` is only updated by promoting `dev`.

## Commit Rules

- Format: `$operator($domain): $message` — e.g. `feat(photo): 사진 업로드 API 추가`
- Operators: `feat` `fix` `refactor` `chore` `test` `docs` `style` `ci`
- Message in Korean. Never add trailers (including Claude-Session). Commit in small, per-task units.

## Conventions

- No comments in code. No emojis anywhere. Documents, labels, and test names in Korean.
- Dependency versions live only in `gradle/libs.versions.toml`. No version strings in build scripts.
- Error codes: `PREFIX-NNN` (`COMMON-001`, `PHOTO-001`). Each domain defines an enum implementing `ErrorCode`.
- API responses use the `ApiResponse` envelope. Throw `BusinessException` subclasses; `GlobalExceptionHandler` converts them.
- No default values in yml placeholders (`${VAR}` only). Defaults live only in `.env.template`. New env vars must be added there.
- `@ConfigurationProperties` data classes get `@Validated` + jakarta validation annotations.
- Validation annotations on data class constructor properties always use the `@field:` use-site (`@field:NotBlank val title: String`); without it Hibernate Validator may not see them. Controllers take `@Valid @RequestBody`.
- Never write a fully-qualified name (FQN) inline. Import the short name whenever there's no naming conflict.

## Planned Conventions

- Primary keys for new tables: `uuid primary key default uuidv7()` (Postgres 18 built-in, time-ordered).
- API versioning: 첫 공개 API(`image/presentation/ImageUploadController.kt`)는 `/api/v1/...` URL 경로 프리픽스로 버저닝했다 — Spring Framework 7 네이티브 API 버저닝(`ApiVersionConfigurer`)은 아직 도입하지 않음. 다음 공개 API를 추가할 때 네이티브 버저닝으로 전환할지, 경로 프리픽스 방식을 계속 쓸지 결정 필요.

## DB Workflow

1. Write `src/main/resources/db/migration/V{yyyyMMddHHmmss}__{description}.sql` — timestamp versions avoid collisions between parallel branches, and `out-of-order: true` lets an older-versioned migration from a merged branch apply later
2. Run `./gradlew flywayMigrate jooqCodegen`
3. Commit generated code in `src/generated/jooq/`

## Directory Index

| Directory | Description |
|-----------|---------|
| `src/` | Sources (see `src/AGENTS.md`) |
| `gradle/` | Version catalog + wrapper (see `gradle/AGENTS.md`) |
| `buildSrc/` | DB build convention plugin (see `buildSrc/AGENTS.md`) |
| `.github/` | CI workflow + templates (see `.github/AGENTS.md`) |
| `.gitattributes` | Marks `src/generated/**` as `linguist-generated` so PR diffs collapse generated files |

## Maintenance

Whenever you add, remove, or change files in a directory, update that directory's AGENTS.md in the same change. Keep this hierarchy accurate at all times.
