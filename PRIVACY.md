# Privacy

[한국어](PRIVACY.ko.md)

Last updated: 2026-11-18

Phantom and the Phantom plugins collect no data about you or your use of the IDE. There are no Phantom servers:
nothing is sent to the Phantom developers, and there is no usage statistics, crash reporting or telemetry service.
Usage statistics cannot be turned on: Phantom ships no usage-statistics consent, so there is nothing to accept and the
uploader never sends.

## What the IDE connects to

Phantom talks to other services only for the features below. Each service handles the request under its own privacy
policy.

| When | Where | What is sent |
| --- | --- | --- |
| A project is opened or its libraries change | GitHub (`raw.githubusercontent.com`): the Phantom plugin list `marketplace.json` | A plain download of the list; no project data. `-Dphantom.plugins.official=false` turns it off |
| You install or update a Phantom plugin, or download a Phantom release | GitHub (release downloads) | A plain download |
| A JSON or YAML file is edited (on by default) | `schemastore.org` JSON schema catalog and the schemas it lists | A plain download. Turn it off in Settings &#124; Languages & Frameworks &#124; Schemas and DTDs &#124; Remote JSON Schemas |
| You download a JDK | `api.foojay.io` and the JDK vendor's download site | The JDK you chose, your OS and architecture |
| You create a Spring Boot project (Spring plugin) | `start.spring.io` | The project options you chose |
| You download a database driver | Maven Central | The driver artifact |
| Only when you ask for it: browsing or installing from the Marketplace tab, downloading language data for grammar checking, opening Choose Boot Java Runtime, Download and Install for a missing Git on Windows | JetBrains (`plugins.jetbrains.com`, `download.jetbrains.com`) or the plugin's download location | The request for that download; Phantom adds no device ID |

Starting the IDE with `-Dphantom.jetbrains.services=true` turns on the JetBrains services that Phantom keeps off by
default (for example feedback forms and the framework library version list). Those requests go to JetBrains and are
governed by the [JetBrains privacy policy](https://www.jetbrains.com/legal/docs/privacy/privacy/).

## AI Integration plugin

The AI Integration plugin runs the AI command-line tool you installed yourself (Claude Code or Codex). The prompts, the
code and the database query results you give it go to that tool's provider (Anthropic or OpenAI) through your own
account, under that provider's terms and privacy policy. Phantom does not see or store them.

## Credentials

Passwords and tokens (for example GitHub tokens and database passwords) are stored in your operating system's keychain
through the IDE's password safe. HTTP Client environment files are ordinary files in your project; anything you put in
them is stored as plain text there.

## Contact

Questions about privacy: open an issue at https://github.com/newkayak12/Phantom/issues.
