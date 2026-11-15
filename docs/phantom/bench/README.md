# Phantom bench

`phantom-bench.sh` measures the installed app on a fixed project; `phantom-bench-report.py` turns the raw output into
numbers and compares them with a baseline. Plan: `docs/plans/2026-10-30-phantom-perf-measure-first.md`.

## Use

```
./phantom-bench.sh --runs 5 --label base
python3 phantom-bench-report.py compare docs/phantom/bench/baseline.json out/phantom-bench/<sha>/base/summary.json

python3 phantom-bench-report.py static "/Applications/Phantom IDEA.app" > /tmp/static.json
python3 phantom-bench-report.py compare docs/phantom/bench/static-baseline.json /tmp/static.json
```

`compare` exits 1 when a metric is worse than the baseline by more than its tolerance.

## Conditions

- Run on a quiet machine. The first smoke run (2026-10-30) ran at a load average of ~400 on 8 cores and took 52 s to
  reach smart mode, against 3.4 s in the 2026-10-17 benchmark; numbers from a loaded machine are not comparable.
- The user's own Phantom may keep running: each bench run has its own config copy (without `.lock`), system and log
  directories. It still competes for CPU, so close it for a baseline.
- Fixture: `--fixture DIR`, else `$PHANTOM_BENCH_FIXTURE`, else `~/projects/private/prototype-reservation-system`;
  copied once to `out/phantom-bench/work/fixture`
  without `mysql_data` and `build/`.

## Baselines

| File | From | Status |
|---|---|---|
| `static-baseline.json` | installed 26.10.27 | recorded |
| `baseline.json` | — | not yet: needs a quiet machine; then calibrate the tolerances below |

## Tolerances

Runtime (`TOLERANCE` in the report script): times +10%, memory and classes +5%, freezes +1, freeze time +500 ms.
Static (`STATIC_TOLERANCE`): bundle size +2%, every count +0.

Calibration, still to do: measure the same build as two sets of 5 runs. If the medians of the two sets differ by more
than a tolerance, raise that tolerance to twice the difference and record the numbers here.
