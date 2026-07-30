<!-- Parent: ../AGENTS.md -->

# docs/api-spec/

API contract source documents.

| File | Description |
|------|---------|
| `api-spec.md` | Source API contract for spec-driven development |

## Rules

- Keep `api-spec.md` as the source API contract document.
- Reflect API behavior changes in `api-spec.md` in the same change as the implementation.
- Each API contract must include URI, HTTP method, required headers/auth, request fields, success response, failure responses, and examples.
- When API changes affect database design or persistence behavior, update `../erd/schema.dbml` in the same change.
- Do not commit generated Swagger UI HTML here unless it is explicitly needed as a source artifact.
- An implementation change is not complete while the API contract document is stale.

Update this file when API spec document files or conventions change.
