# Phantom vs IntelliJ IDEA Ultimate — same project, same method (2026-10-17)

Project: `prototype-reservation-system` (Kotlin, multi-module Gradle). Both IDEs launched from the command line with the
project path, existing caches and settings, measured 180 s after launch. Apple Silicon Mac.

- Phantom: build `c37525a` (DMG `phantom-idea-261017-aarch64.dmg`), user's Phantom config.
- Ultimate: 2026.2 from Toolbox, the user's own config with 40 installed plugins (SonarLint, Detekt, JPA Buddy, Atom Material Icons, ...).
  That is the setup Phantom replaces, so it is the fair comparison for this user, not a bare Ultimate.

| | Phantom | Ultimate | Phantom / Ultimate |
|---|---|---|---|
| First smart mode (`exit dumb mode`) | 3.55 s | 3.03 s | 1.17× |
| Last `exit dumb mode` (indexing settled) | **10.5 s** | 14.8 s | 0.71× |
| Process footprint | **1.83 GB** | 2.71 GB | 0.68× |
| Extra processes | fsnotifier | fsnotifier + SonarLint JVM (378 MB footprint) | |
| Footprint incl. helper JVMs | **1.83 GB** | 3.09 GB | 0.59× |
| Heap used after a full GC (live set) | **341 MB** | 459 MB | 0.74× |
| Loaded classes | **62,086** | 73,346 | 0.85× |
| Class loaders | **1,889** | 2,617 | 0.72× |
| Metaspace used | **343 MB** | 401 MB | 0.86× |
| Code cache used | **54 MB** | 68 MB | 0.80× |

Observed: Phantom is lighter on every memory number and settles indexing 4.3 s earlier. The first smart mode is
0.5 s later than Ultimate's in this run; earlier today a Phantom run reached it at 2.6 s, so this number varies run to
run and needs several runs before drawing conclusions. One run each; not averaged.

Raw output: session scratchpad `bench/phantom-new`, `bench/ultimate` (footprint, GC.heap_info, VM.metaspace, Compiler.codecache, idea.log excerpt).
