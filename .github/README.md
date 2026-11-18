# Repository checks

| Workflow | Checks |
|---|---|
| [Quality](workflows/phantom-quality.yml) | Script and JSON syntax, ShellCheck, workflow validation and quality-tool tests. |
| [Analysis](workflows/phantom-analysis.yml) | Codecov coverage for the quality tool; SonarQube analysis of Phantom sources. |
| [PR routing](workflows/phantom-pr-routing.yml) | Assigns the author and requests other collaborators as reviewers. Drafts wait until ready. |
| [Labels](workflows/phantom-labels.yml) | Synchronizes [labels](labels.json) and labels issues and PRs. |
| [Linked issues](workflows/phantom-close-linked-issues.yml) | Closes linked issues after merge to main. Use `Closes #123` in the PR. |
| [Wrapper validation](workflows/Gradle_wrappers_validation.yml) | Checks Gradle wrapper JARs. |
| [Release](workflows/phantom-release.yml) | Manual product build and release. |

Coverage reports measure [the repository quality tool](../tools/phantom-quality.mjs), not the IDE. Run its tests with `node --test tools/phantom-quality.test.mjs`. For platform changes, run the affected tests described in [DEVELOPMENT.md](../docs/phantom/DEVELOPMENT.md).
