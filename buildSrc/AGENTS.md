<!-- Parent: ../AGENTS.md -->

# buildSrc/

Precompiled convention plugin for database tooling.

| File | Description |
|------|-------------|
| `src/main/kotlin/ppotto.database.gradle.kts` | Applies Flyway + jOOQ codegen plugins. Loads DB credentials from OS env, then `.env`, then defaults. Configures KotlinGenerator (output: `src/generated/jooq`, package `com.github.nexters.ppotto.jooq`), maps `citext` to Kotlin `String`, and excludes migration/extension routines |
| `build.gradle.kts` | Plugin dependencies, all versions resolved from the shared version catalog |
| `settings.gradle.kts` | Imports `../gradle/libs.versions.toml` as `libs` |

## Rules

- `jooqCodegen` is on-demand only; it is never wired into `compileKotlin` (only `mustRunAfter` ordering).
- Generated code excludes `flyway_schema_history`.
- The jOOQ runtime version is aligned via `extra["jooq.version"]` in the root build script; keep plugin and runtime versions identical.

Update this file when build logic in this directory changes.
