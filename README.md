<p align="center">
  <img src="community-resources/phantom-brand-src/phantom-black.png" alt="Phantom" width="180">
</p>

<h1 align="center">Phantom</h1>

<p align="center">A trimmed fork of intellij-community for Kotlin, Spring Boot and JPA backend work.</p>

<p align="center">
  English | <a href="README.ko.md">한국어</a><br>
  <a href="#download-and-install">Download</a> · <a href="#whats-different">What's different</a> · <a href="#performance">Performance</a> · <a href="#plugins">Plugins</a> · <a href="#build-from-source">Build</a> · <a href="#docs">Docs</a>
</p>

<p align="center">
  <a href="LICENSE.txt"><img alt="License: Apache 2.0" src="https://img.shields.io/badge/license-Apache%202.0-blue"></a>
  <a href="#upstream-trademarks-and-clean-room-policy"><img alt="Upstream: idea/262.10968.63" src="https://img.shields.io/badge/upstream-idea%2F262.10968.63-lightgrey"></a>
  <a href="#download-and-install"><img alt="Platforms: macOS arm64, Windows x64" src="https://img.shields.io/badge/platforms-macOS%20arm64%20%7C%20Windows%20x64-lightgrey"></a>
</p>

## What is this

Phantom is a personal fork of [JetBrains/intellij-community](https://github.com/JetBrains/intellij-community), based on tag `idea/262.10968.63` and licensed under Apache 2.0 like its upstream. It removes what a backend developer does not use, turns off automatic calls to JetBrains services, fixes platform issues found by profiling, and moves Ultimate-style backend features (Spring, JPA, HTTP client, database) into clean-room plugins installed on demand. It is not affiliated with or endorsed by JetBrains ([details](#upstream-trademarks-and-clean-room-policy)).

Why the platform and not only the bundle: profiling IntelliJ IDEA Ultimate 2026.2.3 on a Spring Boot project put about 81% of startup cost in the open-source platform itself (the author's own profiling, recorded outside this repository), including a file type change event that rescanned the whole project. The analysis notes live in a separate repository.

## What's different

Each line is backed by a commit; file-level detail is in [FORK-DELTA.md](docs/phantom/FORK-DELTA.md) and [CHANGELOG.md](CHANGELOG.md).

**Removed** ([FORK-DELTA §2.1, §3.3](docs/phantom/FORK-DELTA.md))
- Ultimate promotions: the Java, GitHub and Kotlin promo modules, the TechInsights welcome banner, YAML "Try Ultimate".
- Toolbox App integration, the Junie terminal agent, and the JetBrains links in the Help menu.
- "Report to JetBrains" error submission and the default IDE update feed (`-Didea.updates.url` brings it back).

**Trimmed** ([FORK-DELTA §3.2, §3.3, §3.5](docs/phantom/FORK-DELTA.md))
- 31 upstream plugins out of the bundle: 14 dropped (Learn, MCP server, ACP, internal developer tools, Eclipse import, GitLab, extra keymaps, …), 16 replaced by Phantom-plugins builds (TOML, YAML, Shell Script, Decompiler, Coverage, TextMate, Markdown, Mermaid, Copyright, EditorConfig, HTML Tools, TestNG, Java I18n, Grazie, Terminal, GitHub), and Kotlin, which each release publishes as a separate zip to the Phantom plugins repository instead (it is suggested for projects that use Kotlin or Gradle Kotlin DSL scripts); plus Android and Compose. Sources stay for upstream merges.
- Some changes listed on this page live in this repository's sources of plugins that the bundle now takes from Phantom-plugins builds (GitHub login, Grazie Cloud, the Junie terminal agent, YAML "Try Ultimate", the ShellCheck download). Whether those builds carry them was not checked; see [FORK-DELTA §3.2](docs/phantom/FORK-DELTA.md).
- The Marketplace listing leaves out plugins JetBrains publishes (Phantom ships its own versions of the ones it keeps); running from sources, internal mode or `-Dphantom.marketplace.showAll=true` shows them.
- Automatic JetBrains calls (broken-plugin list, plugin suggestions, Grazie Cloud, completion logs, surveys, feedback forms, log upload) stay off unless `-Dphantom.jetbrains.services=true`.
- Diagnostic files off by default: OpenTelemetry metrics CSV (about 116 MB in 4 days), indexing and storage reports.
- Library text files left out of the Find in Files index: trigram index 369 MB → 84 MB, all indexes 1.2 GB → 916 MB on the author's project.

**Fixed** ([FORK-DELTA §3.5, §3.7, §3.12](docs/phantom/FORK-DELTA.md))
- A bare file type change no longer rescans the whole project (the fix commit `46482b0bf1` names Atom Material Icons as the trigger on every start; the benchmark record marks that attribution as inferred).
- A plugin update no longer re-checks every file on the next open (345k files, about 2.3 s and 20 s of CPU before).
- The consent settings page no longer crashes for a non-JetBrains vendor; Gradle falls back to a supported registered JDK.
- A plugin that keeps another plugin's id as an alias now replaces it: with a Phantom-plugins build and the IntelliJ original both installed, only the Phantom build loads, where the id conflict used to leave both out (commit `04dba66c3a`).

**Added** ([FORK-DELTA §3.1, §3.4, §5.1](docs/phantom/FORK-DELTA.md))
- Own identity: `Phantom.app`, bundle id `dev.phantom.idea`, URL scheme `phantom://`, settings folder `PhantomIDEA`, Phantom Dark/Light themes.
- Plugin repositories from GitHub repos (`owner/repo`), with [Phantom-plugins](https://github.com/newkayak12/Phantom-plugins) always registered.
- Backup and Sync to your own git repository; GitHub login through `gh auth token` instead of account.jetbrains.com.
- Download JDK lists JDKs from the foojay Disco API; heap and file page cache are sized to the machine's RAM.

**What still talks to the network.** Browsing the Marketplace tab (plugins.jetbrains.com) works by design, and Phantom's own plugin repository is read from GitHub (`-Dphantom.plugins.official=false` drops it). Grazie language files, the JBR runtime list and the Git installer list still come from JetBrains, but only when you ask for that download (adding a Grazie language, opening Choose Boot Java Runtime, Download and Install for a missing Git on Windows; on macOS nothing fetches the Git installer list). Known gaps, listed in [ARCHITECTURE.md §6](docs/phantom/ARCHITECTURE.md): usage statistics upload is governed by your consent setting, not by the flag. The framework library version list and the Windows Git update check now stay off JetBrains unless the flag is set; that change is in the source but has not been built or tested yet. Feedback forms and Tips of the Day like/dislike no longer count as gaps: the forms send nothing unless the flag is set, and the like/dislike row is hidden by default. That change is in the source (commit `6e71ed80c4`) but has not been built or tested yet.

## Performance

Median of 5 alternating runs against IntelliJ IDEA Ultimate 2026.2 on 2026-10-17 (Apple Silicon Mac, a warmed copy of one Kotlin multi-module Gradle project, measured 180 s after launch, Phantom build `f2e7052`):

| Median | Phantom | Ultimate | Phantom / Ultimate |
|---|---|---|---|
| First smart mode | 3.4 s | 3.8 s | 0.89× |
| Indexing settled | 4.5 s | 9.0 s | 0.50× |
| IDE process footprint | 1,005 MB | 2,211 MB | 0.45× |
| Heap after full GC | 215 MB | 428 MB | 0.50× |
| Loaded classes | 41.6k | 68.6k | 0.61× |

Limits: one machine, one project, and Ultimate ran with the author's own plugins (SonarLint among them). First smart mode ranges overlap (3.1–3.7 s); the gains come from loading less and not rescanning, not from a faster core. After a plugin update the skipped file check saves CPU, but wall time to open stays about the same (9.8 s before, 10.1 s after; 10 runs, commit `6613a4ed82`). Not yet re-measured on the current build.

Method and raw notes: [benchmark record](docs/phantom/2026-10-17-benchmark-vs-ultimate-median.md), [bench tooling](docs/phantom/bench/README.md).

## Download and install

Builds are on [GitHub Releases](https://github.com/newkayak12/Phantom-IDEA/releases), tagged `v<YY>.<MM>.<DD>.<N>`. There are no Intel Mac or Linux builds.

| Platform | File |
|---|---|
| macOS, Apple Silicon | `phantom-idea-<YYMMDD>.<N>-aarch64.dmg` |
| Windows x64 | `phantom-idea-<YYMMDD>.<N>.exe` (installer) or `.win.zip` |

> [!WARNING]
> Builds are not signed by a recognized authority. The macOS app is ad-hoc signed and not notarized, so Gatekeeper blocks a DMG downloaded in a browser. The Windows files are unsigned, so Windows may warn about an unknown publisher.

On macOS, `./phantom-install.sh --release [tag]` from a clone downloads the DMG with curl (no quarantine flag), replaces `/Applications/Phantom.app` and restarts it; it needs `gh auth login` or `GH_TOKEN`. The ad-hoc signature changes with every build, so macOS would ask for privacy permissions again after each install; to avoid that, the script re-signs the app with a local self-signed certificate (not an Apple certificate, no Apple account) and creates that certificate in its own keychain the first time (`./phantom-sign.sh --setup` does it by hand, `PHANTOM_SIGN_SETUP=0` skips it; see [BUILD.md §5](docs/phantom/BUILD.md)).

The IDE does not check for its own updates; install new releases the same way. On first run you can import settings from an installed JetBrains IDE.

## Plugins

Backend features live in a separate repository, [newkayak12/Phantom-plugins](https://github.com/newkayak12/Phantom-plugins), which every Phantom build registers as a plugin repository. When a project uses a matching library, the IDE suggests the plugin once; you can also install from Settings | Plugins. Seventeen of its plugins are built into the bundle in place of the IntelliJ plugins they replace; all but Mermaid (Phantom's own implementation) keep the IntelliJ plugin ids as aliases, so plugins that depend on the originals still resolve.

| Bundled, built in this repository | Bundled, built in Phantom-plugins | Installed on demand from Phantom-plugins |
|---|---|---|
| `phantom-sync`: Backup and Sync to your own git repository | `toml`, `yaml`, `sh` (Shell Script), `markdown`, `textmate`, `mermaid` | `spring`: beans, injection navigation, autowiring checks |
| `phantom-theme`: Phantom Dark and Light | `decompiler`, `coverage`, `testng`, `java-i18n` | `spring-boot`: `application.yml`/`.properties` completion and docs |
| Endpoints tool window (core; plugins fill it) | `lombok` (replaces the community Lombok plugin bundled before) | `jpa`: JPQL in `@Query`, derived query checks |
| GitHub plugin repository support (core) | `copyright`, `editorconfig`, `htmltools` | `db`: JDBC data sources, SQL console, schema tree |
| | `grazie`, `terminal`, `github` | `http-client`: `.http` request files |
| | | `kotlin`: the IntelliJ Kotlin plugin, built by each release of this repository |
| | | `workspaces`, `tabs`, Phantom Icon Pack |
| | | `bytecode`, `webp`, `modal-commit` (replace the upstream plugins left out of the bundle) |

Planned, not built: Spring runtime (Actuator), Docker and Kubernetes, database migrations and more, designed in [docs/phantom/design/](docs/phantom/design/). Where the moved plugins used to live is listed in [FORK-DELTA §5](docs/phantom/FORK-DELTA.md).

## Build from source

Needs macOS on Apple Silicon, 16 GB+ RAM, about 120 GB free disk for the first build, and read access to Phantom-plugins (a private repository, per the release workflow).

```bash
git clone git@github.com:newkayak12/Phantom-IDEA.git phantom-idea && cd phantom-idea
# Android modules are still referenced by the build graph; fetch the matching tag
git clone --depth 1 --branch idea/262.10968.63 https://github.com/JetBrains/android.git android
# 17 bundled plugins are built from Phantom-plugins next to this checkout (or set PHANTOM_PLUGINS_REPO)
git clone git@github.com:newkayak12/Phantom-plugins.git ../Phantom-plugins
./phantom-build.sh   # PHANTOM_WINDOWS=0 skips the Windows build
```

`phantom-build.sh` first builds those 17 plugins with Phantom-plugins' Gradle wrapper and unpacks them into `out/phantom-plugins/`; it stops if the checkout or a plugin zip is missing. The first build takes about 2 hours; later builds take minutes. Output goes to `out/phantom-idea/artifacts/`, with the unbundled Kotlin plugin as a zip under its `*-plugins/` folder. The build no longer modifies tracked files: `phantom-build.sh` passes the display version as `-Dphantom.version.major` and `-Dphantom.version.minor`, and the tracked `IdeaApplicationInfo.xml` keeps a placeholder (`0` / `0.0`). So the IDE run from sources, or `installers.cmd` run directly, shows version 0.0. This change (commit `048852ca23`) has not been built or tested yet.

Versions: display `YY.MM.DD`, build number `PH-<YYMMDD>.<N>`, and an internal platform build `262.<YYMMDD>` so plugins built for IntelliJ Platform 262 keep loading. Signing, releases and benchmarks: [BUILD.md](docs/phantom/BUILD.md).

## Docs

Most project docs are written in Korean.

- [Documentation index](docs/phantom/README.md): where to start
- [ARCHITECTURE.md](docs/phantom/ARCHITECTURE.md): how the platform starts up and where Phantom code attaches
- [FORK-DELTA.md](docs/phantom/FORK-DELTA.md): every difference from upstream, file by file, and merge risks
- [BUILD.md](docs/phantom/BUILD.md): build, sign, install, release, benchmark
- [DEVELOPMENT.md](docs/phantom/DEVELOPMENT.md): working on the code and running tests
- [CHANGELOG.md](CHANGELOG.md): changes per release

## Upstream, trademarks and clean-room policy

> Phantom is not affiliated with or endorsed by JetBrains. IntelliJ and IntelliJ IDEA are trademarks of JetBrains s.r.o.

Based on intellij-community tag `idea/262.10968.63`. The first commit is the upstream tree (without its README and without upstream history); Phantom changes start after it. [FORK-DELTA §6](docs/phantom/FORK-DELTA.md) lists the files most likely to conflict when a newer upstream tag is merged.

Phantom code is written from intellij-community APIs and public specifications. Ultimate plugins are not bundled, and licensing checks are never touched.

## Contributing

This is a personal fork shaped by one developer's Kotlin and Spring backend work; report problems in this repository's GitHub Issues. No CI runs the tests; run the affected ones locally with `./tests.cmd` (see [DEVELOPMENT.md](docs/phantom/DEVELOPMENT.md)).

## License

Apache License 2.0, same as intellij-community. See [LICENSE.txt](LICENSE.txt) and [NOTICE.txt](NOTICE.txt).
