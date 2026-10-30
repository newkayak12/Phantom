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
set -euo pipefail
cd "$(dirname "$0")"

APP="/Applications/Phantom IDEA.app"
USER_CONFIG="$HOME/Library/Application Support/Phantom/PhantomIDEA"

runs=5
label=base
settle=180
vm_extra=""
fixture_src="$HOME/projects/private/prototype-reservation-system"
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

sha="$(git rev-parse --short HEAD)"
out="$PWD/out/phantom-bench/$sha/$label"
work="$PWD/out/phantom-bench/work"
fixture="$work/fixture"
system="$work/system-$label"
mkdir -p "$out" "$work"

if [[ ! -d "$fixture" ]]; then
  echo "copying the fixture project to $fixture"
  rsync -a --exclude mysql_data --exclude build/ "$fixture_src/" "$fixture/"
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
