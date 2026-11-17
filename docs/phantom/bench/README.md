# Phantom bench

`phantom-bench.sh` measures the installed app on a fixed project; `phantom-bench-report.py` turns the raw output into
numbers and compares them with a baseline. Plan: `docs/plans/2026-10-30-phantom-perf-measure-first.md`.

## Use

```
./phantom-bench.sh --runs 5 --label base
python3 phantom-bench-report.py compare docs/phantom/bench/baseline.json out/phantom-bench/<sha>/base/summary.json

python3 phantom-bench-report.py static "/Applications/Phantom.app" > /tmp/static.json
python3 phantom-bench-report.py compare docs/phantom/bench/static-baseline.json /tmp/static.json
```

`compare` exits 1 when a metric is worse than the baseline by more than its tolerance. With a static baseline it
also checks the baseline's age: when the baseline app's build date (from its build number `262.<YYMMDD>`) is earlier
than the last commit to the bundle list `build/src/org/jetbrains/intellij/build/IdeaCommunityProperties.kt`, it prints
`STALE BASELINE` on stderr and exits 2 if nothing regressed (1 still wins). Plugins removed from the bundle after the
baseline was taken could otherwise come back without failing the gate. The check compares days, so a bundle change
made on the baseline build's own day is not seen. It is skipped with a note on stderr outside a git checkout, when the
baseline's build number carries no date (`262.SNAPSHOT`, an app not built by `phantom-build.sh`), and in a shallow
clone, where the oldest fetched commit would look like the last change to the bundle list (`git fetch --unshallow`
to check). A comment-only commit to the bundle list counts as a change too.

## Conditions

- Run on a quiet machine. The first smoke run (2026-10-30) ran at a load average of ~400 on 8 cores and took 52 s to
  reach smart mode, against 3.4 s in the 2026-10-17 benchmark; numbers from a loaded machine are not comparable.
- The user's own Phantom may keep running: each bench run has its own config copy (without `.lock`), system and log
  directories. It still competes for CPU, so close it for a baseline.
- Fixture: `--fixture DIR`, else `$PHANTOM_BENCH_FIXTURE`, else `~/projects/private/prototype-reservation-system`.
  A relative path is relative to the directory the script is called from (not the repository root it changes to).
  It is copied to `out/phantom-bench/work/fixture` without `mysql_data` and `build/`, and
  `out/phantom-bench/work/fixture.source` records where the copy came from. The copy is kept between runs and labels:
  the same source reuses it; another source (or a copy with no record) replaces it and removes the system directories
  warmed on the old copy (`work/system-*`), so every label warms again. Each case prints one line.
- App: `PHANTOM_BENCH_APP`, else `PHANTOM_APP`, else `/Applications/Phantom.app`; `PHANTOM_BENCH_APP` measures a
  copy that is not installed, such as an unpacked DMG. `PHANTOM_BENCH_PLUGINS` lists plugin zips (space-separated)
  unpacked into each run's config copy, for Kotlin, which a release does not bundle (4d0fa4b060).

## Baselines

| File | From | Status |
|---|---|---|
| `static-baseline.json` | installed 26.10.27 (build `262.261027`) | stale: the bundle list changed after that build (commits from 2026-10-28 on, 1570329708 to ed67704350, then d776e067d6, a936b0feba and cd2a0cf78f, which swap 8 more originals for Phantom-plugins builds, then the comment-only markers of PR #5, 301c21e98a); since 9433fdbe0f Kotlin is no longer bundled, so the regenerated counts are expected to drop, and 9e74cd8d1c adds one `postStartupActivity` (not measured); `compare` exits 2 until it is regenerated |
| `baseline.json` | — | not yet: needs a quiet machine; then calibrate the tolerances below |

Regenerating `static-baseline.json` needs an app built from the current sources (the counts come from the built
bundle, not from the sources), so it needs a full build with the Phantom-plugins checkout next to this one:

```
./phantom-install.sh      # builds HEAD and installs it; or --no-build for a DMG already built from HEAD
python3 phantom-bench-report.py static "${PHANTOM_APP:-/Applications/Phantom.app}" > docs/phantom/bench/static-baseline.json
python3 phantom-bench-report.py compare docs/phantom/bench/static-baseline.json docs/phantom/bench/static-baseline.json
```

The last command must exit 0 (no `STALE BASELINE`). Then update the table above with the new version and build, and
commit the JSON with a message that says why the counts changed.

## Tolerances

Runtime (`TOLERANCE` in the report script): times +10%, memory and classes +5%, freezes +1, freeze time +500 ms.
Static (`STATIC_TOLERANCE`): bundle size +2%, every count +0.

Calibration, still to do: measure the same build as two sets of 5 runs. If the medians of the two sets differ by more
than a tolerance, raise that tolerance to twice the difference and record the numbers here.
