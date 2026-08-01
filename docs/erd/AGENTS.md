<!-- Parent: ../AGENTS.md -->

# docs/erd/

ERD and database design source documents.

| File | Description |
|------|---------|
| `README.md` | ERD document usage and maintenance guide |
| `schema.dbml` | Source DBML schema for the ppotto database design, including analysis status and active-analysis index notes |

## Rules

- Keep `schema.dbml` as the source ERD file.
- Reflect table, column, enum, index, constraint, relationship, deletion policy, and status transition changes in `schema.dbml` in the same change as the implementation.
- Review `schema.dbml` whenever Flyway migrations, jOOQ generated schema, repositories, or domain persistence behavior change.
- Treat the effective Flyway/jOOQ schema as the implementation baseline when syncing this document. Do not model relationships as DBML `ref` unless the database actually has the corresponding foreign key.
- Keep PostgreSQL-specific details that DBML cannot model in the project note or `README.md`.
- When ERD changes affect API behavior, update `../api-spec/api-spec.md` in the same change.
- An implementation change is not complete while the ERD document is stale.

Update this file when ERD document files or conventions change.
