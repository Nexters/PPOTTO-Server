<!-- Parent: ../AGENTS.md -->

# terms

Terms domain. Owns effective term versions and append-only user agreement history.

| Directory | Description |
|-----------|-------------|
| `application/` | `TermsService` current, pending, required validation, and idempotent agreement use cases |
| `domain/` | Pure term and agreement models plus `TERM-*` error codes |
| `infrastructure/` | jOOQ repositories for effective term lookup and idempotent agreement persistence |
| `presentation/` | Version 1 `GET /terms` and `POST /terms/agreements` APIs plus request/response DTOs |

Current versions are selected by the latest `effective_at` at or before the lookup time for each code. Agreement writes never access the user repository and rely on the database foreign key for user integrity. Controllers receive a UUID authentication principal and pass it explicitly to the service.

Update this file when the terms package layout changes.
