<!-- Parent: ../AGENTS.md -->

# storage

Domain-agnostic GCS object key naming utility. No business knowledge of any specific domain (photo, sticker, ...) lives here.

| File | Description |
|------|-------------|
| `ObjectKeyGenerator.kt` | `prefix(vararg pathSegments)` joins segments into a `"a/b/"`-style prefix. `generate(vararg pathSegments, id, extension)` appends `{id}.{extension}` to that prefix |

## Rules

- Pure, stateless `@Component` — no I/O, no domain imports, no content-type/extension mapping knowledge. It only joins path segments; resolving which extension a given content type maps to is a domain concern (e.g. `analysis`'s `PhotoContentType` enum), not this class's.
- Any domain that needs a GCS object key (currently `analysis`'s `PhotoObjectKeys`) wraps this class with its own namespace/segment convention and passes the already-resolved `extension` string.

Update this file when the key-generation rules change.
