<!-- Parent: ../AGENTS.md -->

# storage

Domain-agnostic GCS object key naming and read-URL signing. No business knowledge of any specific domain (photo, sticker, ...) lives here.

| File | Description |
|------|-------------|
| `ObjectKeyGenerator.kt` | `prefix(vararg pathSegments)` joins segments into a `"a/b/"`-style prefix. `generate(vararg pathSegments, id, extension)` appends `{id}.{extension}` to that prefix |
| `GcsReadUrlIssuer.kt` | `issue(objectKeys): Map<objectKey, url>` batch-signs V4 GET URLs against `gcs.bucket`, reuses each object key's URL through Valkey until shortly before expiration, and falls back to direct signing when Valkey is unavailable |
| `ObjectStorageCleaner.kt` | Deletion contract: `deleteByPrefix(prefix)` and `deleteAll(objectKeys)`, both returning the deleted object count |
| `GcsObjectStorageCleaner.kt` | GCS `ObjectStorageCleaner` adapter using one batch delete per call and short-circuiting empty input |

## Rules

- `ObjectKeyGenerator` is a pure, stateless `@Component` — no I/O, no domain imports, no content-type/extension mapping knowledge. It only joins path segments; resolving which extension a given content type maps to is a domain concern (e.g. `analysis`'s `PhotoContentType` enum), not this class's.
- Any domain that needs a GCS object key (currently `analysis`'s `PhotoObjectKeys` and `StickerObjectKeys`) wraps this class with its own namespace/segment convention and passes the already-resolved `extension` string.
- `GcsReadUrlIssuer` is the single place read/GET signed URLs are produced. Every consumer (`analysis`'s `GcsPhotoStorage`, `sticker`'s `GcsStickerImageStorage`) delegates here so the 1-hour read expiration stays in one place. Writes stay with the owning adapter: `GcsPhotoStorage` signs PUT URLs with domain-specific headers (content type, size range) and `GcsStickerStorage` uploads bytes through the SDK, neither of which this class can express.
- Read URLs are cached by bucket and bare object key for 55 minutes with the current 60-minute expiration. The cache lifetime keeps a 5-minute safety margin, uses a smaller proportional margin when the configured expiration is below 50 minutes, and a Valkey failure bypasses the cache instead of failing media reads.
- `issue` signs whatever string it is given as a `BlobId` object name, so callers must pass a bare object key. A `gs://{bucket}/...` URI would be signed as part of the object name and produce a URL that 404s.
- `ObjectStorageCleaner` is an interface, not a concrete class, so tests can register a `@Primary` recording fake and never call the real bucket. `support/ObjectStorageTestConfiguration` does exactly that for every integration test.
- Object deletion is idempotent by design: deleting an absent key or an empty prefix is a no-op, which is what makes the withdrawn-user cleanup batch safe to retry.
- V4 signing is a local RSA operation against the service-account key, so it works in tests with `src/test/resources/dummy-gcs-key.json` without any network call.

Update this file when the key-generation or read-URL signing rules change.
