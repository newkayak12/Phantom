# Phantom vs IntelliJ IDEA Ultimate, second run (2026-10-17 evening)

Same method as [the first run](2026-10-17-benchmark-vs-ultimate.md), with stricter conditions:

- Project: a copy of `prototype-reservation-system` (Kotlin, multi-module Gradle), so neither IDE touches the real `.idea`.
- Each IDE opened the copy once to warm its indexes, quit, then was launched again and measured 180 s after launch.
- Phantom: build `f2e7052` (DMG `phantom-idea-261017-aarch64.dmg`), a copy of the user's Phantom config.
- Ultimate: 2026.2 (Toolbox), the user's own config with their installed plugins (SonarLint, JPA Buddy, ...).
- "After GC" numbers are taken right after `jcmd GC.run`. Apple Silicon Mac, one measured run each.

| | Phantom | Ultimate | Phantom / Ultimate |
|---|---|---|---|
| First smart mode (`exit dumb mode`) | **2.2 s** | 3.3 s | 0.67× |
| Last `exit dumb mode` (indexing settled) | **3.9 s** | 21.3 s | 0.18× |
| IDE process footprint | **1.37 GB** | 2.42 GB | 0.57× |
| Heap used after a full GC (live set) | **310 MB** | 502 MB | 0.62× |
| Loaded classes | **59.9k** | 86.1k | 0.70× |
| Class loaders | **1,959** | 3,190 | 0.61× |
| Metaspace used | **328 MB** | 471 MB | 0.70× |
| Code cache used | **56 MB** | 70 MB | 0.80× |

Child processes at 180 s:

| | Phantom | Ultimate |
|---|---|---|
| fsnotifier | 8 MB | 8 MB |
| JCEF `cef_server` | 180 MB RSS | none |
| SonarLint server JVM | none | 445 MB RSS |
| Gradle daemon | none | 921 MB RSS (Ultimate re-ran a Gradle sync on reopen) |

Observed:
- Phantom is ahead on every number in the IDE process. Its indexing settles 17 s earlier.
- Ultimate's footprint is lower than in the first run (2.71 → 2.42 GB). This run opened a warmed copy of the project instead of the
  project the user had open, so the numbers are not comparable across the two runs; compare only within one table.
- The Gradle daemon is counted separately: it serves builds, not the editor, and Phantom skipped the sync because the
  copy was already imported.

Why Phantom had a JCEF process (checked after the run):
- Not a startup cost of Phantom. The copied Phantom config had `README.md` open as the current tab in Markdown
  preview mode (`split_layout="SHOW_PREVIEW"`), so the restored tab rendered the preview, which runs in JCEF.
  The stack trace in idea.log goes `MarkdownPreviewFileEditor.attachHtmlPanel` -> `JCEFHtmlPanel` -> `JBCefApp`.
- Ultimate's own workspace had no Markdown tab open, so it never started the browser.
- So Phantom did extra work in this run; without the preview tab its footprint would be about 180 MB lower.

Raw output: session scratchpad `bench/phantom-v2`, `bench/ultimate-v2` (footprint, GC.heap_info before and after GC,
VM.metaspace, Compiler.codecache, VM.classloader_stats, child processes, idea.log excerpt).
