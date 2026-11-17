# Phantom repository automation

All workflows are maintained for this fork. The [prototype-reservation-system workflows](https://github.com/newkayak12/prototype-reservation-system/actions) informed the layout; its Gradle tasks and service credentials are not shared with Phantom.

| Workflow | Behavior |
|---|---|
| `phantom-quality.yml` | Test the quality tool; check changed scripts, JSON, Bash errors and workflow syntax on PRs and main pushes |
| `phantom-analysis.yml` | Generate and retain LCOV for the Node quality tool; upload to Codecov and run SonarQube when configured |
| `phantom-pr-routing.yml` | Assign PR author; request other human collaborators when ready for review |
| `phantom-labels.yml` | Sync configured labels without deleting other labels; add title/file labels to issues and PRs |
| `phantom-close-linked-issues.yml` | After a merge into the default branch, close open issues in this repository returned by GitHub's closing-issue links |
| `phantom-release.yml` | Manually build and publish on the existing self-hosted release runner |

Metadata workflows use `pull_request_target` without checking out PR code. They begin handling events after the workflows reach the default branch. Label definitions are read from that branch's [labels.json](labels.json); run **Phantom labels** manually to sync definitions. Labeling is additive: title/file edits do not remove manually applied or previously matched labels. PR author/reviewer assignment is described in [rulesets/README.md](rulesets/README.md).

Link an issue with `Closes #123`, `Fixes #123`, or the PR sidebar's closing-issue relationship. GitHub normally closes these on default-branch merge itself; the workflow is idempotent and ignores already closed issues, cross-repository issues, plain mentions and merges to other branches. An unmerged closed PR does not close issues.

## SonarQube and Codecov setup

Create/enable the Phantom project in each service, then configure repository **Settings → Secrets and variables → Actions**:

| Kind | Name | Value |
|---|---|---|
| Secret | `CODECOV_TOKEN` | Upload token for the Phantom repository in Codecov |
| Secret | `SONAR_TOKEN` | Analysis token with permission for the Phantom SonarQube project |
| Variable | `SONAR_HOST_URL` | Reachable SonarQube server URL, or `https://sonarcloud.io` for Cloud |
| Variable | `SONAR_PROJECT_KEY` | Existing SonarQube project key |
| Variable | `SONAR_ORGANIZATION` | Organization key, required for SonarQube Cloud |

SonarQube variables and both service tokens were registered on 2026-11-17. If configuration is missing, the analysis workflow still produces a measured coverage artifact and explicitly reports that uploads/analysis **did not run**. Fork PRs receive no upload credentials. SonarQube PR analysis also requires an edition/plan that supports it. Configured uploads or quality-gate failures fail CI.

README badges use the default `main` branch. Workflow badges describe workflow completion, not whether an external upload ran. Codecov and SonarQube badges link to the registered projects; private repositories may require a service-specific badge token. Never put analysis or upload secrets into badge URLs.

Coverage is for `tools/phantom-quality.mjs` only, generated from the seven Node tests. It does not represent IntelliJ/JVM product coverage. [codecov.yml](../codecov.yml) isolates this report under `phantom-quality`. Before scanning, the workflow restricts its checkout to Phantom-owned source paths because Cloud's new scope mode ignores source/test inclusions. [sonar-project.properties](../sonar-project.properties) excludes generated files; Kotlin/Python coverage is excluded until their tests supply actual reports.

Branch rules are separate from these Actions. [rulesets/main.json](rulesets/main.json) is the supplied `protect-main` export with zero required approvals and administrator bypass added; importing it into GitHub remains subject to GitHub's feature availability.
