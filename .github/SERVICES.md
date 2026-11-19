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

The current release workflow uses the owner's trusted self-hosted runner and its Git credentials to retrieve bundled artifacts. Do not attach this runner to arbitrary PR execution.

The separate, pending cross-repository publishing change requires `PHANTOM_PLUGINS_TOKEN` in Phantom, scoped to **Phantom-plugins → Contents: write and Pull requests: write**. It is not part of the current analysis configuration. Do not copy an interactive personal token into CI as a substitute. The ordinary `GITHUB_TOKEN` is restricted to its own repository.

See [workflow checks](WORKFLOWS.md) for the maintained jobs.
