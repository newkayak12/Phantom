<p align="center">
  <img src="community-resources/phantom-brand-src/phantom-black.png" alt="Phantom IDEA" width="180">
</p>

# Phantom IDEA

A lighter IDE on the IntelliJ Platform, built for Kotlin + Spring Boot + JPA backend work.

Phantom IDEA is a personal fork of [JetBrains/intellij-community](https://github.com/JetBrains/intellij-community)
(Apache 2.0). It removes what a backend developer does not use, fixes platform issues found by profiling,
and adds the backend features that otherwise need IntelliJ IDEA Ultimate as **clean-room** plugins.

> Phantom IDEA is not affiliated with or endorsed by JetBrains. IntelliJ and IntelliJ IDEA are trademarks of JetBrains s.r.o.

## Why

Profiling IntelliJ IDEA Ultimate 2026.2.3 on a Spring Boot project showed:

| Finding | Measured |
|---|---|
| Startup usable at 16.2 s instead of 5.5 s: a "File type change" event invalidates all indexes twice and rescans ~500k files | startup JFR |
| ~620 MB of native memory pinned by the file page cache (600 MiB default) | NMT |
| Feature modules load whether the project uses the technology or not (JS stack ~5k classes, unused framework plugins, 60 SQL dialects) | class loader dump |
| CPU saturated for the first 15 s by sidecar processes (SonarLint JVM, daemons, JCEF, git) | JFR, ps |

About 81% of startup cost is in the open-source platform itself, so Phantom fixes the platform, not only the bundle.
Analysis notes live in a separate repository (Intellij-rea).

## What is different from intellij-community

Done:

- Branding: name, icons, splash, `Phantom IDEA.app`, bundle id `dev.phantom.idea`, URL scheme `phantom://`, separate settings folder
- Removed Ultimate upsell entries (locked Spring wizard, GitHub and Kotlin promos, welcome-screen survey banner)
- Removed Android, Compose and declarative Android Gradle from the default bundle
- Fixed a platform bug: the consent settings page crashed for any non-JetBrains vendor (`AppUIUtil.loadConsentsForEditing`)
- No full rescan when a plugin fires a bare file type change that changes nothing (Atom Material Icons did this on every start, ~2 s of extra scanning); real association, ignore-list and announced changes still rescan
- Backup and Sync to your own git repository (`phantom-sync`)

Planned: see [docs/phantom/2026-10-16-feature-matrix.md](docs/phantom/2026-10-16-feature-matrix.md) and the design docs in
[docs/phantom/design/](docs/phantom/design/).

| Plugin | Covers |
|---|---|
| `phantom-spring` | Moved to [Phantom-plugins](https://github.com/newkayak12/Phantom-plugins) (`spring`): beans, injection navigation, autowiring inspection, Endpoints |
| `phantom-spring-boot` | Moved to [Phantom-plugins](https://github.com/newkayak12/Phantom-plugins) (`spring-boot`): `application.yml`/`.properties` completion and docs |
| `phantom-spring-runtime` | Actuator, runtime beans and properties, DevTools |
| `phantom-jpa` | Moved to [Phantom-plugins](https://github.com/newkayak12/Phantom-plugins) (`jpa`): JPQL in `@Query`, derived queries, `@Modifying` and mapping checks, repository navigation |
| `phantom-db` | Moved to [phantom-plugins](https://github.com/newkayak12/Phantom-plugins): installed on demand |
| `phantom-http` | Moved to [Phantom-plugins](https://github.com/newkayak12/Phantom-plugins) (`http-client`): `.http` request files, environments, `import`/`run`, checks |
| `phantom-sync` | Backup and Sync to your own git repository (GitHub or any remote) instead of a JetBrains account |
| `phantom-tabs` | Moved to [Phantom-plugins](https://github.com/newkayak12/Phantom-plugins) (`tabs`): editor tab groups as in a browser, name, color, collapse, drag in and out, per split |
| `phantom-workspaces` | Moved to [Phantom-plugins](https://github.com/newkayak12/Phantom-plugins) (`workspaces`): all open projects as tabs of one window, project windows stacked at one size and position |
| `phantom-containers` | Docker, Compose, Kubernetes |

Design rules for every Phantom plugin:

1. Activate only when the project uses the technology
2. Update models incrementally; never invalidate the whole project on one edit
3. No EDT work, no startup activity unless needed, no network on the startup path

## Indexing

Phantom keeps every index the platform has, but makes it cheaper to keep them current.

**Find in Files index (trigrams) leaves libraries out.** The platform already left `.java` sources in libraries
out of the trigram index. Phantom leaves out every text file in a library (Kotlin sources, XML, properties and so on).
Find in Files still searches libraries: files that are not in the index are read directly, as `.java` library sources
always were. Project files are unchanged. On a Spring Boot project the trigram index went from 369 MB to 84 MB, and
all indexes together from 1.2 GB to 916 MB.

**A plugin update no longer rescans every file.** When a project opens, the platform skips checking each file
if nothing that affects indexing changed since the last session. It decides this from a fingerprint that
covers the IDE build, the index versions and every installed plugin's version. Updating any plugin changed the
fingerprint, so the next open checked every file again (345k files on a Spring Boot project: about 2.3 s of
background work and 20 s of CPU). Phantom counts only plugins that register something that changes what or how
files are indexed: indexes, stubs, file types, parsers, word scanners, syntax highlighters, decompilers, roots,
excludes and file property pushers. Updating, adding or removing any other plugin keeps the fingerprint. The list
is broad on purpose: counting a plugin by mistake only costs one full check after its update, while leaving one out
by mistake would keep stale index data.

Reopening a project after a plugin update, 10 runs each, same machine, isolated caches:

| | before | after |
|---|---|---|
| Files checked on open | 345,122 | 0 |
| Checking time | 2,319 ms ± 98 | 36 ms ± 14 |
| CPU time of the whole session | 36.4 s ± 1.4 | 16.5 s ± 0.8 |
| Wall time (the check runs in the background) | 9.8 s | 10.1 s |

After an update of a plugin that does affect indexing (for example `db`, which adds the SQL file type), every file
is still checked, as before.

## Clean-room policy

Ultimate binaries were decompiled **only to understand behavior and performance** (personal analysis, not part of this repository).
Phantom code is written from intellij-community APIs and public specifications. No decompiled JetBrains code is copied here,
Ultimate plugins are not bundled, and licensing checks are never touched.

## Building

Requirements: macOS (Apple Silicon tested), 16 GB+ RAM (48 GB used), ~120 GB free disk for the first build.

```bash
git clone git@github.com:newkayak12/Phantom-IDEA.git phantom-idea
cd phantom-idea
# Android modules are still referenced by the build graph; fetch the matching tag
git clone --depth 1 --branch idea/262.10968.63 https://github.com/JetBrains/android.git android
./phantom-build.sh
```

The first build takes about 2 hours (Bazel downloads and compiles everything); later builds take minutes.
Output: `out/phantom-idea/artifacts/phantom-idea-<YYMMDD>.<N>-aarch64.dmg` (Apple silicon only; the newest DMG is
kept, and the `.sit` archives and `out/phantom-idea/temp` are removed after each build).

### Releases

`./phantom-release.sh` builds, checks the app in the DMG, and publishes it as a GitHub release `v<YY>.<MM>.<DD>.<N>`
(`--no-build` releases the last build). The **Phantom release** workflow does the same from the Actions tab; it runs
on a self-hosted runner on your Mac, since a GitHub-hosted macOS runner (7 GB memory, small disk, 10x minutes on a
private repository) cannot build this. Register one once with `tools/phantom-runner-setup.sh`.

The DMG's app is ad-hoc signed. `phantom-install.sh` re-signs it with the local `Phantom Local` certificate (see
`phantom-sign.sh`), so macOS keeps its privacy permissions across builds.

### Versioning

| | Example | Note |
|---|---|---|
| Build number | `PH-261018.2` | Phantom's own: build date and the build of that day. Shown in About, Info.plist and the artifact names |
| Display version | `26.10.18` | shown in About and the title bar |
| Platform build | `262.261018` | internal only: the IDE declares it as `apiVersion` and bundled plugins get `since/until-build` from it, so plugins built for IntelliJ Platform 262 (`since-build="262"`) keep loading |
| Artifacts | `phantom-idea-261018.2-aarch64.dmg` | |
| Settings folder | `PhantomIDEA` | fixed, so settings survive version bumps |

`phantom-build.sh` stamps the date and the build of the day automatically.

## Upstream

Based on intellij-community tag `idea/262.10968.63`. The first commit is the upstream tree (without its README);
Phantom changes start after it.

## License

Apache License 2.0, same as intellij-community. See [LICENSE.txt](LICENSE.txt) and [NOTICE.txt](NOTICE.txt).
