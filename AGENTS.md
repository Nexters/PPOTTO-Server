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
| `docker compose -f compose.deploy.yaml -f compose.dev.yaml up -d --build` | Run the shared Dev server stack |
| `docker compose -f compose.deploy.yaml -f compose.production.yaml up -d --build` | Run the Production server stack |

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
- Typed identifiers (`global/identifier/Ids.kt`, e.g. `UserId`) are used end to end: domain models, repository public signatures, application services, cross-domain ports, and presentation (DTO id fields, `@PathVariable`, `@AuthenticatedUser`/`@CurrentUser`). JSON and OpenAPI representations stay unwrapped plain uuid strings.
- jOOQ id columns are generated as typed identifiers via codegen `forcedType` converters (`global/jooq/`), so DSL bindings and `toDomain` mappings take typed ids directly and column-level transposition fails to compile. Raw `UUID` remains only at boundaries: the SecurityContext principal, external representations (JWT, Redis) inside adapters, and the analysis-internal UUID surface, which wraps ids at DSL binding points.
- New id types are added only for ids crossing method boundaries; purely internal ids stay `UUID` (see `src/main/kotlin/com/github/nexters/ppotto/global/identifier/AGENTS.md`).

## Spec-Driven Development

- `docs/api-spec/api-spec.md` (API contracts) and `docs/erd/schema.dbml` (DB design) are the source of truth. Check them before implementing an endpoint or table.
- If the design changes during implementation, update the relevant `docs/` file in the same change. See `docs/AGENTS.md` for the full rules.

## Branch & PR Rules

- Branch: off `dev`, named `feat/<issue-number>-<short-feature-description>` (e.g. `feat/1-user-board-image-entity`).
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
- No default values in yml placeholders (`${VAR}` only). Defaults live only in `.env.template`.
- Adding a `${VAR}` placeholder to any `src/main/resources/config/*.yml` means updating three files in the same change: `.env.template`, `src/test/resources/application-test.yml`, and the AOT cache training `RUN` step in `Dockerfile`. The `Dockerfile` step refreshes a real `prod`-profile Spring context, so an unresolved placeholder fails `docker build` and breaks CD even though `./gradlew build` still passes — this is the only one of the three that no test catches.
- Values in the `Dockerfile` AOT step are throwaway and must be obviously fake (`aot`, `http://localhost`, `dummy-*`). Never a real secret, key, or credential: this repository is public and secret-scanned. They only need to satisfy format validation (`@NotBlank`, `@Positive`, `@Size`, URI parsing). When a property must point at a parseable file, bind-mount the committed dummy fixture from `src/test/resources/` (`dummy-gcs-key.json`, `dummy-apple-key.p8`) instead of inventing a value.
- `@ConfigurationProperties` data classes get `@Validated` + jakarta validation annotations.
- Put each primary-constructor property on its own line. Property annotations go on separate lines immediately above the property, never on the same line as `val` or `var`. Separate each annotated property group from the next with one blank line, so a property reads as one block instead of blurring into the annotations above it (`ktlint_standard_no-blank-line-in-list` is disabled repo-wide in `.editorconfig` to allow this).
- Validation annotations on data class constructor properties always use the `@field:` use-site; without it Hibernate Validator may not see them. Controllers take `@Valid @RequestBody`.
- Never write a fully-qualified name (FQN) inline. Import the short name whenever there's no naming conflict.
- API versioning: the version is specified via the `X-API-Version` request header (Spring Framework 7 native API versioning, `WebMvcConfigurer.configureApiVersioning`, configured in `global/config/WebMvcConfig.kt`). A missing header defaults to `1`. URL paths carry no version and no `/api` prefix (e.g. `/analysis`). Each controller declares `version = "N"` on its class-level `@RequestMapping`.

## Planned Conventions

- Primary keys for new tables: `uuid primary key default uuidv7()` (Postgres 18 built-in, time-ordered).

## DB Workflow

1. Write `src/main/resources/db/migration/V{yyyyMMddHHmmss}__{description}.sql` — timestamp versions avoid collisions between parallel branches, and `out-of-order: true` lets an older-versioned migration from a merged branch apply later
2. Run `./gradlew flywayMigrate jooqCodegen`
3. Commit generated code in `src/generated/jooq/`

## Directory Index

| Directory | Description |
|-----------|---------|
| `src/` | Sources (see `src/AGENTS.md`) |
| `docs/` | Spec-driven development documents, including API contracts and ERD design (see `docs/AGENTS.md`) |
| `e2e/` | Python E2E test suite for the photo analysis pipeline, including design notes and generated reports (see `e2e/AGENTS.md`) |
| `gradle/` | Version catalog + wrapper (see `gradle/AGENTS.md`) |
| `buildSrc/` | DB build convention plugin (see `buildSrc/AGENTS.md`) |
| `.github/` | CI workflow + templates (see `.github/AGENTS.md`) |
| `.gitattributes` | Marks `src/generated/**` as `linguist-generated` so PR diffs collapse generated files |
| `.gitignore` | Ignores local build/tooling files, secrets, `.env`, and generated E2E report artifacts |
| `compose.yaml` | Local PostgreSQL 18 + pgvector |
| `compose.deploy.yaml` | Shared server deployment stack: Caddy + API + PostgreSQL + Valkey (Redis-compatible, backs refresh token storage) |
| `compose.dev.yaml` | Dev deployment overrides; mounts GCS credentials from `../secrets` |
| `compose.production.yaml` | Production deployment overrides; mounts GCS credentials from `../secrets` |
| `Caddyfile` | Shared automatic HTTPS and reverse proxy configuration |
| `Dockerfile` | Layered JDK 25 image. The AOT cache training step refreshes a full `prod`-profile context, so it must carry every config env var plus build-only mounted dummy GCS and Apple credentials |
| `.env.template` | Local environment defaults, including a non-production provider-token encryption key, GCS upload/read signed URL expirations, external-service timeouts, and the disabled-by-default withdrawn-user cleanup schedule |
| `build.gradle.kts` | Single-module build, including Spring Security, Redis, JWT, OAuth, and authenticated MockMvc test support |

## Maintenance

Whenever you add, remove, or change files in a directory, update that directory's AGENTS.md in the same change. Keep this hierarchy accurate at all times.
