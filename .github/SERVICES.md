# Service configuration

Use repository **Settings → Secrets and variables → Actions**. Keep upload tokens in secrets, never in Markdown or badge URLs.

| Setting | Type | Phantom value or purpose |
|---|---|---|
| `CODECOV_TOKEN` | Secret | Upload token for `newkayak12/Phantom` in Codecov |
| `SONAR_TOKEN` | Secret | Analysis token authorized for this SonarQube project |
| `SONAR_HOST_URL` | Variable | `https://sonarcloud.io` |
| `SONAR_PROJECT_KEY` | Variable | `newkayak12_Phantom` |
| `SONAR_ORGANIZATION` | Variable | `newkayak-reservation` |

Enable the existing repository in Codecov. In SonarQube Cloud, select the existing organization and project rather than creating another GitHub organization binding. Set the project's New Code definition in **Administration → New Code**; use Previous version when maintaining release versions, or a chosen day window for continuous delivery.

## Verify an upload

1. Run **Phantom analysis** from Actions on `main`.
2. Check the test summary and coverage artifact, then the Codecov upload and SonarQube steps. A successful local report alone does not prove an external upload succeeded.
3. Confirm the same commit in the Codecov project and SonarQube activity. The Codecov flag is `phantom-quality`; coverage measures `tools/phantom-quality.mjs`, not the IDE/JVM.

Fork PRs run tests without upload credentials. Missing configuration fails trusted analysis runs. Dependabot PRs may also lack repository secrets; verify analysis on the merged `main` commit rather than exposing credentials through `pull_request_target`.

## Diagnose failures

| Symptom | Check |
|---|---|
| Missing configuration | Exact secret/variable names above, in this repository |
| Upload rejected | Token's project access, expiration and service repository access |
| Sonar quality gate failed | Project's New Code findings and coverage requirements; this is separate from authentication |
| Badge unavailable | Project visibility and the service's badge-specific access token; never reuse an upload token |
| Ruleset API returns 403 | GitHub plan support for private-repository rulesets; importing JSON does not activate enforcement |

## Release credentials

The release workflow runs on a GitHub-hosted macOS runner. It releases this repository with the job's `GITHUB_TOKEN` and, optionally, `PHANTOM_PLUGINS_TOKEN` in Phantom, a fine-grained token scoped to **Phantom-plugins → Contents: write and Pull requests: write**, to release Kotlin and Maven there and merge the marketplace.json pull request. Do not copy an interactive personal token into CI as a substitute. The ordinary `GITHUB_TOKEN` is restricted to its own repository.

`PHANTOM_RELEASE_TOKEN`, also optional, is a fine-grained token scoped to **Phantom → Contents: write and Workflows: write**. The release checkout keeps it for git, so the release tag is pushed with it: GitHub rejects a tag pushed with `GITHUB_TOKEN` when the tagged commit changed `.github/workflows`, since that token has no workflows permission. Without it the job's token pushes the tag, and releasing such a commit fails at the tag push. `PHANTOM_PLUGINS_TOKEN` cannot stand in: it reaches Phantom-plugins only.

See [workflow checks](WORKFLOWS.md) for the maintained jobs.
