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
