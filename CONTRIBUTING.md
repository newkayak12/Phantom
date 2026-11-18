# Contributing to Phantom

Report reproducible problems and propose changes in [GitHub Issues](https://github.com/newkayak12/Phantom/issues). Include the affected version, platform, steps, expected behavior and relevant logs with personal data removed. For security vulnerabilities, follow [SECURITY.md](SECURITY.md).

Before changing code, read [AGENTS.md](AGENTS.md) and the affected module's guidance. Platform patches and bundle selection belong here; most plugin features belong in the separate Phantom-plugins repository. Read [DEVELOPMENT.md](docs/phantom/DEVELOPMENT.md) for setup, source ownership and test commands.

Plan a new plugin, module or larger change in a GitHub issue rather than a document under `docs/`, and reference it from each pull request (`Part of #N`, the last one `Closes #N`) so it closes on merge. Keep private data out of issues.

Keep the change focused, preserve compatibility and license notices, and put user-facing strings in resource bundles. Describe the behavior changed and the reason. Add an entry under 미배포 in CHANGELOG.md. Run affected tests with ./tests.cmd, specifying the test module and fully qualified test name; use Bazel compilation when no tests apply. Documentation-only changes need link and diff checks rather than an IDE build.

Use a plain imperative English commit subject, for example `Fix plugin alias resolution`. Include the motivation and verification for substantial changes. YouTrack tickets, JetBrains commit prefixes and Patronus/SafePush are not required for this project. Open a pull request with exact checks run and any unverified behavior; do not publish releases or change versions as an incidental part of a fix.

Claude and Codex share [AGENTS.md](AGENTS.md); [CLAUDE.md](CLAUDE.md) imports it for Claude Code. [.ai/workflow.md](.ai/workflow.md) describes task routing. Personal settings and credentials belong in ignored local files.

Phantom derives from JetBrains intellij-community. Upstream copyright, license and attribution notices remain in LICENSE.txt, NOTICE.txt and the relevant source files; this contribution guide describes the independently maintained Phantom project.

