# ppotto Server

Backend server for ppotto (뽀또). Kotlin 2.3 / Spring Boot 4.1 / JDK 25 / PostgreSQL 18 / Flyway / jOOQ / Kotest. Single Gradle module.

## Commands

| Command | Description |
|---|---|
| `./gradlew build` | Compile + tests + ktlint + detekt. Must pass before finishing any task |
| `./gradlew ktlintFormat` | Auto-fix code style violations |
| `./gradlew flywayMigrate jooqCodegen` | Apply migrations + regenerate jOOQ code (needs local DB: `docker compose up -d`) |
| `./gradlew bootRun` | Run locally (reads `.env` via spring-dotenv) |

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

## DB Workflow

1. Write `src/main/resources/db/migration/V{n}__{description}.sql`
2. Run `./gradlew flywayMigrate jooqCodegen`
3. Commit generated code in `src/generated/jooq/`

## Directory Index

| Directory | Description |
|-----------|---------|
| `src/` | Sources (see `src/AGENTS.md`) |
| `gradle/` | Version catalog + wrapper (see `gradle/AGENTS.md`) |
| `buildSrc/` | DB build convention plugin (see `buildSrc/AGENTS.md`) |
| `.github/` | CI workflow + templates (see `.github/AGENTS.md`) |

## Maintenance

Whenever you add, remove, or change files in a directory, update that directory's AGENTS.md in the same change. Keep this hierarchy accurate at all times.
