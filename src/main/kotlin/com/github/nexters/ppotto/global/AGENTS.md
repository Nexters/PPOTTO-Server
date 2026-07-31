<!-- Parent: ../AGENTS.md -->

# global

Shared module used by all domains. Contains no business logic.

| Directory | Description |
|-----------|---------|
| `config/` | Spring configuration beans (see `config/AGENTS.md`) |
| `error/` | Error codes, exceptions, global handler (see `error/AGENTS.md`) |
| `jooq/` | Custom jOOQ converters used by codegen `forcedType` |
| `logging/` | Request logging filter (see `logging/AGENTS.md`) |
| `openapi/` | Reusable Swagger error response annotations (see `openapi/AGENTS.md`) |
| `response/` | Response envelope models (see `response/AGENTS.md`) |
| `security/` | Required and optional authenticated-user MVC argument contracts (see `security/AGENTS.md`) |
| `storage/` | `ObjectKeyGenerator`, `GcsReadUrlIssuer`, and `ObjectStorageCleaner` — domain-agnostic GCS object key/prefix building, read signed URL issuing, and object deletion shared across domains (see `storage/AGENTS.md`) |

## Rules

- Nothing here may depend on a domain package. Dependencies flow from domains into global only.
- Additions here affect every domain; prefer putting logic in a domain unless it is truly cross-cutting.

Update this file when subpackages are added or removed.
