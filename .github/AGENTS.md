<!-- Parent: ../AGENTS.md -->

# .github/

CI workflow and GitHub templates. All user-facing text in Korean.

| File | Description |
|------|-------------|
| `workflows/ci.yml` | CI: Gradle build, tests, lint, and Kover XML on PR/push to `main` or `dev`; posts a coverage comment on PRs via mi-kas/kover-report |
| `workflows/cd.yml` | CD: after a successful push CI, selects branch settings once, builds and pushes an immutable SHA-tagged registry image, deploys it with the shared Compose stack over pinned-host-key SSH, then checks Actuator health |
| `PULL_REQUEST_TEMPLATE.md` | PR template (Korean) |
| `ISSUE_TEMPLATE/bug_report.yml` | Bug report form, auto-labels `버그` |
| `ISSUE_TEMPLATE/feature_request.yml` | Feature request form, auto-labels `기능` |
| `ISSUE_TEMPLATE/config.yml` | Blank issues disabled |

## Rules

- CI must stay green; a failing `./gradlew build` locally will fail CI identically.
- Keep JavaScript actions on Node 24-compatible major versions.
- Repo settings: squash merge only, branch auto-delete, ruleset "main 보호" requires PR (0 approvals, review threads must be resolved). Admins may bypass.

Update this file when workflows or templates change.
