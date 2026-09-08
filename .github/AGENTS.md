<!-- Parent: ../AGENTS.md -->

# .github/

CI workflow and GitHub templates. All user-facing text in Korean.

| File | Description |
|------|-------------|
| `workflows/ci.yml` | CI: Gradle build, tests, lint, and Kover XML on PR/push to `production`; posts a coverage comment on PRs via mi-kas/kover-report |
| `workflows/cd.yml` | CD: after a successful push CI on `production`, builds and pushes an immutable SHA-tagged registry image, deploys it with `compose.production.yaml` over pinned-host-key SSH through the `production` GitHub Environment, then checks Actuator health |
| `PULL_REQUEST_TEMPLATE.md` | PR template (Korean) |
| `ISSUE_TEMPLATE/bug_report.yml` | Bug report form, auto-labels `버그` |
| `ISSUE_TEMPLATE/feature_request.yml` | Feature request form, auto-labels `기능` |
| `ISSUE_TEMPLATE/config.yml` | Blank issues disabled |

## Rules

- CI must stay green; a failing `./gradlew build` locally will fail CI identically.
- Keep JavaScript actions on Node 24-compatible major versions.
- Repo settings: squash merge only, branch auto-delete. `production` is the default and only long-lived branch; the "production 보호" ruleset blocks deletion and force-push and requires a PR (org admins may bypass). Every push to `production` deploys the single server through the `production` GitHub Environment.

Update this file when workflows or templates change.
