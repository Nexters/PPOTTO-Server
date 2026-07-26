<!-- Parent: ../AGENTS.md -->

# .github/

CI workflow and GitHub templates. All user-facing text in Korean.

| File | Description |
|------|-------------|
| `workflows/ci.yml` | CI: `build` job (gradle build = tests + lint + kover xml; posts a coverage comment on PRs via mi-kas/kover-report) on PR and main push; `docker` job (image build with GHA cache) on main push only |
| `workflows/pr-automation.yml` | On PR opened: assigns the author, parses the issue number from the branch name (`feat/N-...`) to append `Closes #N` (Development link + auto-close) and copy the issue's labels to the PR. Skips silently when the branch has no issue number |
| `CODEOWNERS` | All paths owned by both team members — auto-requests review from the non-author |
| `PULL_REQUEST_TEMPLATE.md` | PR template (Korean) |
| `ISSUE_TEMPLATE/bug_report.yml` | Bug report form, auto-labels `버그` |
| `ISSUE_TEMPLATE/feature_request.yml` | Feature request form, auto-labels `기능` |
| `ISSUE_TEMPLATE/config.yml` | Blank issues disabled |

## Rules

- CI must stay green; a failing `./gradlew build` locally will fail CI identically.
- Repo settings: squash merge only, branch auto-delete, ruleset "main 보호" requires PR (0 approvals, review threads must be resolved). Admins may bypass.

Update this file when workflows or templates change.
