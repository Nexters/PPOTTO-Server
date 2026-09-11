# e2e

Python E2E test suite for the photo analysis pipeline.

## Conventions

- Keep test scripts and design documents under `e2e/` tracked in Git.
- Generated E2E outputs live under `e2e/reports/` and are not committed.
- Python cache files under `e2e/__pycache__/` are not committed.
- When adding or changing E2E behavior, update `README.md` or `E2E_TEST_DESIGN.md` if the usage, scenario coverage, or output location changes.
- The main pipeline script may optionally verify theme-based sticker regeneration with `--theme-query` and `--regenerate-theme`; keep report fields aligned with that scenario.
- The main pipeline script supports grouped upload requests through `--group-size`, so high-count tests can exercise burst groups without exceeding the API's 100-group limit.
- The main pipeline script accepts JPEG, PNG, and WEBP photos for new uploads; HEIC files are intentionally excluded from E2E input discovery.
- Seed photos live in `e2e/photos/` (gitignored) by default; `PPOTTO_E2E_PHOTOS_DIR` and `--photos-dir` override it. Never hardcode a personal path.
- The report carries a copy score section (banned-stem rate, length, degrade counts, vocabulary variety) and writes `e2e/reports/copy_score_<ts>.json` next to the HTML report.
- `--compare <before-id> <after-id>` scores two analyses from the database alone and prints a delta table; it needs neither the API server nor Vertex AI credentials.
- `COPY_BANNED_STEMS` is deliberately duplicated from the production prompt's banned list. Do not share one source: a scorer that reads the prompt's list measures the prompt against itself.
- Do not compare runs across a commit that edits `COPY_BANNED_STEMS`; the yardstick moved.
