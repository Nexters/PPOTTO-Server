<!-- Parent: ../AGENTS.md -->

# src/

Source root.

| Directory | Description |
|-----------|---------|
| `main/kotlin/com/github/nexters/ppotto/` | Application code (see `main/kotlin/com/github/nexters/ppotto/AGENTS.md`) |
| `main/resources/` | Configuration and migrations (see `main/resources/AGENTS.md`) |
| `generated/jooq/` | jOOQ generated code for the current Flyway schema, including boards and drawings; id columns are generated as `global/identifier/` typed ids via `forcedType` converters. Never edit by hand; regenerate with `./gradlew flywayMigrate jooqCodegen` and commit |
| `test/kotlin/com/github/nexters/ppotto/` | Tests (see `test/kotlin/com/github/nexters/ppotto/AGENTS.md`) |
| `test/resources/` | Test profile environment values, including OAuth/JWT/Vertex AI timeouts, and parseable dummy provider credential fixtures |

Update this file when the source layout changes.
