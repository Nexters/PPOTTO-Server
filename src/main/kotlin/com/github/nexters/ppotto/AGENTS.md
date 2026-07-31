<!-- Parent: ../../../../../../AGENTS.md -->

# com.github.nexters.ppotto

Application root package. One top-level subpackage = one domain.

| File / Directory | Description |
|------|-------------|
| `PpottoApplication.kt` | Boot entrypoint. `@ConfigurationPropertiesScan` enabled |
| `global/` | Shared module: config, error, logging, response (see `global/AGENTS.md`) |
| `user/` | User account domain: social identity, encrypted provider token, withdrawal, and cleanup lifecycle (see `user/AGENTS.md`) |
| `board/` | Board domain, 1:N with User: `domain`/`infrastructure` only so far (see `board/AGENTS.md`) |
| `analysis/` | Analysis domain, owns `Analysis` and `Photo` (1:N with Board): full DDD-lite layout (see `analysis/AGENTS.md`) |
| `auth/` | Social OAuth, service token, refresh rotation, and Bearer authentication domain (see `auth/AGENTS.md`) |

## Adding a new domain

Create `<domain>/` here with the DDD-lite layout. Reference example (`photo`):

```
photo/
├── presentation/
│   ├── PhotoController.kt           endpoints; takes request dto, calls Service, returns ApiResponse
│   └── dto/
│       ├── PhotoCreateRequest.kt    request dto; jakarta validation annotations live here
│       └── PhotoResponse.kt         response dto; never expose jOOQ-generated types
├── application/
│   ├── PhotoService.kt              write use cases; @Transactional boundary; builds/validates domain model
│   └── PhotoQueryService.kt         read-only; assembles dto directly via jOOQ projections
├── domain/
│   ├── Photo.kt                     pure Kotlin domain model with state and rules
│   └── PhotoErrorCode.kt            enum implementing ErrorCode, codes like PHOTO-001
└── infrastructure/
    └── PhotoRepository.kt           DSLContext-based persistence; only place touching the DB
```

Failure points throw semantic exceptions: `throw NotFoundException(PhotoErrorCode.PHOTO_NOT_FOUND)` — the global handler converts them.

Checklist for a new domain:

1. Write the Flyway migration (`V{n}__{description}.sql`)
2. Run `./gradlew flywayMigrate jooqCodegen` and commit generated code
3. Implement layers following the example above
4. Add integration tests extending `IntegrationTest`
5. Create an AGENTS.md for the new domain package (parent: `../AGENTS.md`)

Update this file when packages are added or removed.
