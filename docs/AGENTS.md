<!-- Parent: ../AGENTS.md -->

# docs/

Project documentation for spec-driven development.

| Directory | Description |
|-----------|---------|
| `api-spec/` | API contract documentation used as the source of truth before implementation (see `api-spec/AGENTS.md`) |
| `erd/` | ERD and database design documents (see `erd/AGENTS.md`) |

## Rules

- `docs/` under this repository is always the source of truth, even when a document references an external origin (e.g. an exported HTML/Notion doc, such as the origin link at the top of `api-spec.md`). If they diverge, follow `docs/` here.
- Keep API contract documents in Markdown.
- Keep implementation and every document under `docs/` in sync in the same change.
- If API behavior changes, update `api-spec/api-spec.md` in the same change. This includes URI, HTTP method, headers/auth, request fields, response data, status codes, error codes, and examples.
- Keep ERD source documents in DBML unless another format is explicitly required.
- If database design or persistence behavior changes, update `erd/schema.dbml` in the same change. This includes tables, columns, enums, indexes, constraints, relationships, deletion policy, and status transitions.
- An implementation change is not complete while the related `docs/` contract or design document is stale.
- Each API contract must include URI, HTTP method, required headers/auth, request fields, success response, failure responses, and examples.
- Do not commit generated Swagger UI HTML here unless it is explicitly needed as a source artifact.

Update this file when the documentation layout changes.
