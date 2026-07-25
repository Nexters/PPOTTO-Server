<!-- Parent: ../AGENTS.md -->

# global

Shared module used by all domains. Contains no business logic.

| Directory | Description |
|-----------|---------|
| `config/` | Spring configuration beans (see `config/AGENTS.md`) |
| `error/` | Error codes, exceptions, global handler (see `error/AGENTS.md`) |
| `logging/` | Request logging filter (see `logging/AGENTS.md`) |
| `response/` | Response envelope models (see `response/AGENTS.md`) |

## Rules

- Nothing here may depend on a domain package. Dependencies flow from domains into global only.
- Additions here affect every domain; prefer putting logic in a domain unless it is truly cross-cutting.

Update this file when subpackages are added or removed.
