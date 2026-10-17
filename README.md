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

## What is different from intellij-community

Done:

- Branding: name, icons, splash, `Phantom IDEA.app`, bundle id `dev.phantom.idea`, URL scheme `phantom://`, separate settings folder
- Removed Ultimate upsell entries (locked Spring wizard, GitHub and Kotlin promos, welcome-screen survey banner)
- Removed Android, Compose and declarative Android Gradle from the default bundle
- Fixed a platform bug: the consent settings page crashed for any non-JetBrains vendor (`AppUIUtil.loadConsentsForEditing`)

Planned: see [docs/phantom/2026-10-16-feature-matrix.md](docs/phantom/2026-10-16-feature-matrix.md) and the design docs in
[docs/phantom/design/](docs/phantom/design/).

| Plugin | Covers |
|---|---|
| `phantom-spring` | Bean model, injection navigation, gutter icons, autowiring inspections, events |
| `phantom-spring-boot` | Initializr wizard, `application.yml`/`.properties` completion, run configs, Endpoints |
| `phantom-spring-runtime` | Actuator, runtime beans and properties, DevTools |
| `phantom-jpa` | Entities, JPQL/HQL in `@Query`, Spring Data derived queries |
| `phantom-db` | Moved to [phantom-plugins](https://github.com/newkayak12/Phantom-plugins): installed on demand |
| `phantom-http` | `.http` request files, environments, requests from endpoints |
| `phantom-containers` | Docker, Compose, Kubernetes |

Design rules for every Phantom plugin:

1. Activate only when the project uses the technology
2. Update models incrementally; never invalidate the whole project on one edit
3. No EDT work, no startup activity unless needed, no network on the startup path

## Clean-room policy


Phantom code is written from intellij-community APIs and public specifications. Ultimate plugins are not bundled, and licensing checks are never touched.

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
Output: `out/phantom-idea/artifacts/phantom-idea-<YYMMDD>-aarch64.dmg`.

### Versioning

| | Example | Note |
|---|---|---|
| Build number | `262.261016` | `262` is the IntelliJ platform branch, kept so Marketplace plugins (`since-build="262"`) still install. The rest is the build date |
| Display version | `26.10.16` | shown in About and the title bar |
| Artifacts | `phantom-idea-261016-aarch64.dmg` | |
| Settings folder | `PhantomIDEA` | fixed, so settings survive version bumps |

`phantom-build.sh` stamps the date automatically.

## Upstream

Based on intellij-community tag `idea/262.10968.63`. The first commit is the upstream tree (without its README);
Phantom changes start after it.

## License

Apache License 2.0, same as intellij-community. See [LICENSE.txt](LICENSE.txt) and [NOTICE.txt](NOTICE.txt).
