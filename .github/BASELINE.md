# Repository baseline

This is the foundation for the Phantom fork, not an assertion that the entire upstream IDE has been tested. Workflow changes take effect on the default branch after the PR merges.

| Area | Configuration and scope |
|---|---|
| Quality | `phantom-quality.yml`: changed-file whitespace, JSON/script syntax, ShellCheck and actionlint; runner regression tests. |
| Analysis | `phantom-analysis.yml`: selected Phantom-owned sources in a sparse checkout. Codecov measures the Node quality runner; JVM coverage is not provided. |
| Product builds | `phantom-release.yml`: explicit manual release on the existing self-hosted Mac. Ordinary PR checks do not build or test the entire IDE. |
| AI | Root `AGENTS.md`, thin `CLAUDE.md`, `.ai/workflow.md`, fork-specific local skills. Shared instructions are tracked; local state and credentials are ignored. |
| Collaboration | Author assignment, collaborator review requests, additive labels and closing-linked-issue automation. These workflows execute metadata only. |
| Contributor intake | PR template, bug/feature forms, contributor and security guidance. |
| Branch policy | `.github/rulesets/main.json`: PR required, zero approvals, admin bypass. Live enforcement is blocked by the private repository's GitHub plan (HTTP 403). |
| Repository settings | Default `main`, squash merge enabled, other merge methods disabled, merged branches deleted; Actions default token read-only, workflow PR approval disabled. |
| Documentation | English/Korean README, status badges and Phantom build/development docs. Measured benchmark values remain unchanged. |

SonarQube and Codecov credentials are Actions secrets, not repository files. Badges follow `main`; a PR report does not create a main-branch Codecov result. Do not add required status-check names until those jobs exist and have passed, and do not describe the prepared ruleset as enforced while the API rejects it.

GitHub Actions updates are grouped weekly by Dependabot. The inherited Gradle wrapper check now runs for wrapper changes in PRs and on `main`. Phantom-managed workflows use pinned action commits. The inherited Jewel workflow is path-filtered to Jewel and its workflow/setup action; its component build/tests remain separate, and it still contains upstream tag references. Jewel's YouTrack-token/single-commit formalities run only in the upstream repository because Phantom uses its own commit policy.

## Reference adaptation

| Reference workflow family | Phantom adaptation |
|---|---|
| Module tests, build, daily tests | Tool checks run on PR/main; full IDE build remains manual on the existing build runner. No scheduled full IDE build is enabled. |
| Publish test results | Tool test output and LCOV artifact are retained by Actions. There is no claim of published IDE JVM test results. |
| SonarQube / Codecov | Connected projects, scoped sources and measured tool coverage; no fabricated product coverage. |
| Assignee, labels, issue closing | Metadata-only author/reviewer routing, additive labels and closing-reference handling. |
| PR size/conflict | Reviewers can inspect GitHub's diff size and mergeability; no extra size/conflict bot or persistent labels are configured. |
| Stale closure / TODO-to-issue | Omitted to avoid closing user reports or creating issues automatically without a repository policy. |
| Claude review / Slack | Omitted: no paid AI review credentials or external message integration is configured. |

The remaining product-test step is to validate the focused memory/sync targets documented in [automation details](README.md) on a trusted build runner before choosing required PR checks. Repository-wide Kotlin style/Detekt and JVM coverage remain outside this baseline.
