#!/bin/zsh
# Measures the installed Phantom IDEA on a fixed project.
#   ./phantom-bench.sh [--runs 5] [--label base] [--settle 180] [--vm-extra FILE] [--fixture DIR]
# Each run starts the app with its own copy of the user's config and log directory, so the user's running IDE
# is untouched. The system directory (caches, indexes) is kept per label and warmed by one discarded run, as in
# docs/phantom/2026-10-17-benchmark-vs-ultimate-median.md. After --settle seconds the raw outputs (idea.log,
# the startup report from idea.log.perf.stats.file, jcmd, footprint) are saved and turned into run-N.json by
# phantom-bench-report.py.
# VM options: the launcher reads the bundled idea.vmoptions first, then the file in $IDEA_VM_OPTIONS
# (native/XPlatLauncher/src/default.rs, collect_vm_options_from_files), so the user's own options and --vm-extra
# override bundled ones the same way a user's idea.vmoptions does.
# Environment (each defaults to the value in parentheses, so nothing needs to be set on the author's machine):
#   PHANTOM_APP            the app to measure (/Applications/Phantom IDEA.app)
#   PHANTOM_CONFIG_DIR     the user config copied into each run ($HOME/Library/Application Support/Phantom/PhantomIDEA)
#   PHANTOM_BENCH_FIXTURE  the project to open, unless --fixture is given ($HOME/projects/private/prototype-reservation-system)
# A relative --fixture or PHANTOM_BENCH_FIXTURE is relative to the directory this script is called from.
set -euo pipefail
caller_dir="$PWD"
cd "$(dirname "$0")"

# PHANTOM_BENCH_APP measures another copy of the app (an unpacked DMG) and wins over PHANTOM_APP; PHANTOM_BENCH_PLUGINS
# lists plugin zips unpacked into each run's config copy (Kotlin, which a release does not bundle)
APP="${PHANTOM_BENCH_APP:-${PHANTOM_APP:-/Applications/Phantom IDEA.app}}"
USER_CONFIG="${PHANTOM_CONFIG_DIR:-$HOME/Library/Application Support/Phantom/PhantomIDEA}"

runs=5
label=base
settle=180
vm_extra=""
fixture_src="${PHANTOM_BENCH_FIXTURE:-$HOME/projects/private/prototype-reservation-system}"
while (( $# )); do
  case "$1" in
    --runs) runs="$2"; shift 2 ;;
    --label) label="$2"; shift 2 ;;
    --settle) settle="$2"; shift 2 ;;
    --vm-extra) vm_extra="$2"; shift 2 ;;
    --fixture) fixture_src="$2"; shift 2 ;;
    *) echo "unknown argument: $1"; exit 2 ;;
  esac
done
[[ "$fixture_src" == /* ]] || fixture_src="$caller_dir/$fixture_src"
fixture_src="${fixture_src:A}"

sha="$(git rev-parse --short HEAD)"
out="$PWD/out/phantom-bench/$sha/$label"
work="$PWD/out/phantom-bench/work"
fixture="$work/fixture"
system="$work/system-$label"
mkdir -p "$out" "$work"

# The copy is kept between runs and labels, so every run opens the same project at the same path, the one the
# system directories below were warmed on. fixture.source names the directory it was copied from: the same source
# reuses the copy as it is; another one (or none recorded) replaces it, and the system directories warmed on the old
# copy are removed with it, so each label warms again on the new one.
source_file="$work/fixture.source"
copied_from=""
[[ -f "$source_file" ]] && copied_from="$(<"$source_file")"
if [[ -d "$fixture" && "$copied_from" == "$fixture_src" ]]; then
  echo "reusing the fixture copy in $fixture (copied from $fixture_src)"
else
  [[ -d "$fixture_src" ]] || { echo "error: the fixture project '$fixture_src' is not a directory" >&2; exit 1; }
  if [[ "$copied_from" != "$fixture_src" && ( -d "$fixture" || -n "$(print -r -- "$work"/system-*(N))" ) ]]; then
    echo "replacing the fixture copy in $fixture (copied from ${copied_from:-an unrecorded source}) with $fixture_src; any warmed system directories are removed too"
    rm -rf "$work"/system-*(N)
  else
    echo "copying the fixture project $fixture_src to $fixture"
  fi
  rm -rf "$fixture" "$source_file"
  rsync -a --exclude mysql_data --exclude build/ "$fixture_src/" "$fixture/"
  print -r -- "$fixture_src" > "$source_file"
fi

if pgrep -f "$APP/Contents/MacOS/idea" >/dev/null; then
  echo "note: another Phantom is running; it competes for CPU and adds noise"
fi

# One run: $1 = run directory. Prints nothing; leaves raw files in the run directory.
run_once() {
  local dir="$1"
  rm -rf "$dir"
  mkdir -p "$dir/log"
  local config; config="$(mktemp -d)"
  # .lock belongs to the user's running IDE; copying it makes the bench instance refuse to start
  rsync -a --exclude .lock "$USER_CONFIG/" "$config/"
  for zip in ${=PHANTOM_BENCH_PLUGINS:-}; do unzip -qo "$zip" -d "$config/plugins"; done

  print -l "idea.config.path=$config" "idea.system.path=$system" "idea.log.path=$dir/log" > "$dir/idea.properties"
  {
    [[ -f "$USER_CONFIG/idea.vmoptions" ]] && cat "$USER_CONFIG/idea.vmoptions"
    print -- "-Didea.log.perf.stats.file=$dir/perf.json"
    print -- "-Didea.trust.all.projects=true"
    [[ -n "$vm_extra" ]] && cat "$vm_extra"
  } > "$dir/bench.vmoptions"

  local t0; t0="$(python3 -c 'import time; print(int(time.time()*1000))')"
  IDEA_PROPERTIES="$dir/idea.properties" IDEA_VM_OPTIONS="$dir/bench.vmoptions" \
    "$APP/Contents/MacOS/idea" "$fixture" >"$dir/stdout.txt" 2>&1 &
  local pid=$!
  print -l "{\"pid\": $pid, \"launch_epoch_ms\": $t0, \"settle_s\": $settle, \"sha\": \"$sha\", \"label\": \"$label\"}" > "$dir/meta.json"

  sleep "$settle"
  if ! kill -0 "$pid" 2>/dev/null || [[ ! -f "$dir/log/idea.log" ]]; then
    echo "run $dir: the IDE did not start; see $dir/stdout.txt"
    kill -KILL "$pid" 2>/dev/null || true
    rm -rf "$config"
    return 1
  fi
  jcmd "$pid" GC.run >/dev/null
  jcmd "$pid" GC.heap_info > "$dir/heap.txt"
  jcmd "$pid" PerfCounter.print > "$dir/perfcounters.txt"
  footprint -p "$pid" > "$dir/footprint.txt" 2>&1 || true

  kill -TERM "$pid"
  for _ in {1..30}; do kill -0 "$pid" 2>/dev/null || break; sleep 1; done
  kill -0 "$pid" 2>/dev/null && kill -KILL "$pid"
  wait "$pid" 2>/dev/null || true
  rm -rf "$config"
}

if [[ ! -f "$system/.bench-warm" ]]; then
  echo "warming the system directory for '$label' (discarded run)"
  run_once "$out/warmup"
  touch "$system/.bench-warm"
fi

for i in $(seq 1 "$runs"); do
  echo "run $i/$runs"
  run_once "$out/run-$i"
  python3 phantom-bench-report.py run "$out/run-$i" > "$out/run-$i.json"
done
python3 phantom-bench-report.py show "$out"/run-*.json
python3 phantom-bench-report.py summary "$out"
echo "summary: $out/summary.json"
