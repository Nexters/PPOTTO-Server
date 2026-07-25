<!-- Parent: ../AGENTS.md -->

# gradle/

Version catalog and Gradle wrapper.

| File | Description |
|------|-------------|
| `libs.versions.toml` | Single source of truth for all dependency and plugin versions. buildSrc imports the same catalog |
| `wrapper/` | Gradle wrapper 9.6.1. Do not edit manually; use `gradle wrapper --gradle-version <v>` |

## Rules

- Every new dependency goes here first, then gets referenced as `libs.*` in build scripts.
- Modules managed by the Spring Boot BOM are declared without a version.
- Gradle plugin marker artifacts for buildSrc use the `gradle-plugin-*` alias convention.

Update this file when the catalog structure or wrapper changes.
