# e2e

Python E2E test suite for the photo analysis pipeline.

## Conventions

- Keep test scripts and design documents under `e2e/` tracked in Git.
- Generated E2E outputs live under `e2e/reports/` and are not committed.
- Python cache files under `e2e/__pycache__/` are not committed.
- When adding or changing E2E behavior, update `README.md` or `E2E_TEST_DESIGN.md` if the usage, scenario coverage, or output location changes.
- The main pipeline script may optionally verify theme-based sticker regeneration with `--theme-query` and `--regenerate-theme`; keep report fields aligned with that scenario.
- The main pipeline script supports grouped upload requests through `--group-size`, so high-count tests can exercise burst groups without exceeding the API's 100-group limit.
