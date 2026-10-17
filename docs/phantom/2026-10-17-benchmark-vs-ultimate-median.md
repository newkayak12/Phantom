# Phantom vs IntelliJ IDEA Ultimate, median of 5 runs (2026-10-17)

Method:
- Project: a warmed copy of `prototype-reservation-system` (Kotlin, multi-module Gradle).
- Phantom: build `f2e7052`, a copy of the user's Phantom config.
- Ultimate: 2026.2, the user's own config and plugins.
- Neither IDE restores an open editor tab. The tab that made Phantom start JCEF in the [second run](2026-10-17-benchmark-vs-ultimate-v2.md) was removed from the copy.
- The two IDEs alternate, 5 rounds (20:41–21:15). Each run is measured 180 s after launch; "after GC" means right after `jcmd GC.run`.
- Apple Silicon Mac.

| Median (min–max) | Phantom | Ultimate | Phantom / Ultimate |
|---|---|---|---|
| First smart mode | **3.4 s** (2.9–3.7) | 3.8 s (3.1–4.1) | 0.89× |
| Indexing settled (last `exit dumb mode`) | **4.5 s** (4.4–5.1) | 9.0 s (8.5–9.4) | 0.50× |
| IDE process footprint | **1,005 MB** (979–1,200) | 2,211 MB (2,122–2,256) | 0.45× |
| Footprint incl. helper processes | **1,013 MB** | 2,665 MB (+ SonarLint JVM ~447 MB) | 0.38× |
| Heap after full GC (live set) | **215 MB** (214–219) | 428 MB (421–436) | 0.50× |
| Loaded classes | **41.6k** | 68.6k | 0.61× |
| Class loaders | **1,446** | 2,473 | 0.58× |
| Metaspace used | **227 MB** | 375 MB | 0.61× |
| Code cache used | **41 MB** | 56 MB | 0.73× |

Observed:
- Phantom is ahead on every median. The ranges never overlap, except first smart mode, where they overlap from 3.1 to 3.7 s.
- First smart mode is close: 0.4 s apart at the median. The large gains are in indexing time and memory.
- Ultimate's first run loaded fewer classes than its later runs: 64.0k against 68.0–68.8k.
- Compared with the second run, Phantom's memory dropped by about a third (1.37 GB → 1.0 GB, 59.9k → 41.6k classes).
  That run restored a Markdown preview tab: JCEF plus the Markdown preview cost about 370 MB and 18k classes.

Raw output: session scratchpad `bench/runs/{p,u}1..5`; medians computed by `bench/median.py`.

## Why the numbers differ

Sources:
- `idea.log` from all 10 runs.
- One extra run per IDE under the same conditions, with `jcmd VM.classes`.
- Each loaded class was mapped to the plugin or `lib/` jar that ships it (scratchpad `bench/attr.py`, `bench/ownidx.py`).
- Phantom 36.7k mapped classes, Ultimate 59.1k.

### Indexing settles at 4.5 s against 9.0 s (observed)

- **Ultimate scans the project twice.** All 5 Ultimate runs log
  `Invalidating all indexing flags. Reason: File type change: added - null, removed - null` about 5 s in. That cancels the scan in progress and starts a full one again. No Phantom run has it.
  - Phantom's own icon pack does not change file types, so it never triggers this.
- **Ultimate scans more files**: 433,459 against 388,766, about 45k more. They come from indexable sets that Ultimate's extra plugins contribute.
- Both IDEs found the same ~1,630 files to index, so the indexing work itself is the same; only the scanning differs.

### 22k more classes in Ultimate (observed counts)

| Owner | Phantom | Ultimate | Extra |
|---|---|---|---|
| Kotlin plugin | 5,307 | 9,601 | +4,294 |
| JDK + lambdas/hidden classes | 12,636 | 18,174 | +5,538 (follows the rest) |
| Platform `lib/` | 15,205 | 18,208 | +3,003 |
| JavaScript, CSS | 0 | 1,291 | +1,291 |
| SonarLint (in the IDE; its server JVM is separate) | 0 | 1,164 | +1,164 |
| Database Tools | 0 | 858 | +858 |
| Full Line | 0 | 524 | +524 |
| User plugins: JWT, Big Data Tools, Detekt, AsciiDoc, Translation, Atom Icons, StringManipulation | 0 | ~2,040 | +2,040 |
| Remote Dev (cwm), Docker, Kubernetes, shared indexes, platform daemon, Station | 0 | ~1,130 | +1,130 |
| Spring, JavaEE, JPA, microservices, Swagger, Micronaut, app servers | 0 | ~400 | +400 |

- **Kotlin**:
  - observed: Ultimate has resolved Kotlin code with the K2 compiler front end within 3 minutes, with no editor open. It loads
    `kotlin.fir.*` (resolve, scopes, declarations), `analysis.api` and light classes: about 3,000 classes Phantom never loads.
  - inferred: a background consumer asks Kotlin for resolved types at startup. The candidates are Spring and JPA model
    building, microservices endpoint scanning, Detekt and SonarLint. Phantom's Spring support only resolves on demand.
- **Platform `lib/`**:
  - observed: Ultimate-only libraries are loaded: the Ktor HTTP client and TLS (640), gRPC (237), JSVG (311),
    `polySymbols` (217), `product-backend` (173), the statistics uploader (173) and Backup and Sync (116).
  - observed: the larger `ide.impl`, `lang.impl` and `ide` counts (+1,000) are the platform code those plugins call into.

### Memory follows the classes (inferred)

- Metaspace +148 MB and code cache +15 MB come from the extra classes.
- The live heap doubles (215 → 428 MB): every plugin above keeps its services, caches and indexes, and Kotlin K2 keeps
  its resolve sessions.
- The SonarLint server is a separate ~445 MB JVM on top.

### What this means for Phantom

- First smart mode is close (3.4 s against 3.8 s) because both run the same platform startup. Phantom's gains come
  from not loading what the project does not use and from not rescanning, not from a faster core.
- Two rules keep Phantom ahead:
  - a new feature never triggers whole-project Kotlin resolution at startup;
  - nothing fires a file type change after startup.
