#!/usr/bin/env python3
"""Turns the raw outputs of phantom-bench.sh into numbers.

  phantom-bench-report.py run <run-dir>        one run's raw files -> JSON on stdout
  phantom-bench-report.py show <run.json>...   a table of runs
  phantom-bench-report.py summary <label-dir>  run-*.json -> summary.json (median, min, max per metric)
  phantom-bench-report.py static <App.app>     bundle size, jar and plugin counts, startup registrations -> JSON
  phantom-bench-report.py compare <baseline.json> <summary.json>
                                               exit 1 when a median is worse than the baseline's beyond its tolerance;
                                               exit 2 when nothing regressed but a static baseline is stale (its app
                                               was built before the last change to the bundle list)
"""
import json
import re
import statistics
import subprocess
import sys
import zipfile
from pathlib import Path

# idea.log: "2026-10-30 14:05:01,123 [   4512]   INFO - #c.i.o.p.DumbServiceImpl - exit dumb mode [fixture]";
# the bracketed number is milliseconds since the IDE started.
LOG_LINE = re.compile(r"^\S+ \S+ \[\s*(\d+)\]")
FROZEN = re.compile(r"UI was frozen for (\d+)ms")


def parse_log(path):
    exits, freezes = [], []
    for line in path.read_text(errors="replace").splitlines():
        if "exit dumb mode" in line:
            m = LOG_LINE.match(line)
            if m:
                exits.append(int(m.group(1)))
        m = FROZEN.search(line)
        if m:
            freezes.append(int(m.group(1)))
    return exits, freezes


def startup_top(path, limit=20):
    """Named items with a duration, from the startup report, most expensive first."""
    if not path.exists():
        return []
    found = []

    def walk(node, section):
        if isinstance(node, dict):
            name = node.get("name", node.get("n"))
            duration = node.get("duration", node.get("d"))
            if isinstance(name, str) and isinstance(duration, (int, float)):
                found.append({"section": section, "name": name, "ms": duration})
            for key, value in node.items():
                walk(value, key if isinstance(value, (list, dict)) else section)
        elif isinstance(node, list):
            for value in node:
                walk(value, section)

    # the IDE appends one report per startup phase to the same file, so it can hold several JSON documents
    text, decoder, pos = path.read_text(), json.JSONDecoder(), 0
    while (pos := len(text) - len(text[pos:].lstrip()) ) < len(text):
        document, pos = decoder.raw_decode(text, pos)
        walk(document, "")
    found.sort(key=lambda item: item["ms"], reverse=True)
    return found[:limit]


def heap_used_mb(path):
    m = re.search(r"garbage-first heap\s+total reserved \d+K, committed \d+K, used (\d+)K", path.read_text())
    return round(int(m.group(1)) / 1024) if m else None


def loaded_classes(path):
    counters = dict(re.findall(r"^(java\.cls\.\w+)=(\d+)$", path.read_text(), re.M))
    if "java.cls.loadedClasses" not in counters:
        return None
    get = lambda key: int(counters.get(key, 0))
    return (get("java.cls.loadedClasses") + get("java.cls.sharedLoadedClasses")
            - get("java.cls.unloadedClasses") - get("java.cls.sharedUnloadedClasses"))


def footprint_mb(path):
    m = re.search(r"Footprint:\s*([\d.,]+)\s*(KB|MB|GB)", path.read_text())
    if not m:
        return None
    value = float(m.group(1).replace(",", ""))
    return round(value * {"KB": 1 / 1024, "MB": 1, "GB": 1024}[m.group(2)])


def run(run_dir):
    run_dir = Path(run_dir)
    meta = json.loads((run_dir / "meta.json").read_text())
    exits, freezes = parse_log(run_dir / "log" / "idea.log")
    return {
        "sha": meta["sha"],
        "label": meta["label"],
        "first_smart_mode_ms": exits[0] if exits else None,
        "indexing_settled_ms": exits[-1] if exits else None,
        "footprint_mb": footprint_mb(run_dir / "footprint.txt"),
        "live_heap_mb": heap_used_mb(run_dir / "heap.txt"),
        "loaded_classes": loaded_classes(run_dir / "perfcounters.txt"),
        "freezes": len(freezes),
        "freeze_total_ms": sum(freezes),
        "startup_top": startup_top(run_dir / "perf.json"),
    }


METRICS = ["first_smart_mode_ms", "indexing_settled_ms", "footprint_mb", "live_heap_mb", "loaded_classes",
           "freezes", "freeze_total_ms"]


def show(paths):
    rows = [json.loads(Path(p).read_text()) for p in paths]
    print("metric".ljust(22) + "".join(Path(p).stem.rjust(10) for p in paths))
    for metric in METRICS:
        print(metric.ljust(22) + "".join(str(row[metric]).rjust(10) for row in rows))
    if rows and rows[0]["startup_top"]:
        print("\nstartup report, top 10 (first run):")
        for item in rows[0]["startup_top"][:10]:
            print(f"  {item['ms']:>7} ms  {item['section']:<24} {item['name']}")


def summary(label_dir):
    label_dir = Path(label_dir)
    rows = [json.loads(p.read_text()) for p in sorted(label_dir.glob("run-*.json"))]
    result = {"sha": rows[0]["sha"], "label": rows[0]["label"], "runs": len(rows), "metrics": {}}
    for metric in METRICS:
        values = [row[metric] for row in rows if row[metric] is not None]
        if values:
            result["metrics"][metric] = {"median": statistics.median(values), "min": min(values), "max": max(values)}
    costs = {}
    for row in rows:
        for item in row["startup_top"]:
            costs.setdefault((item["section"], item["name"]), []).append(item["ms"])
    result["startup_top"] = sorted(
        ({"section": section, "name": name, "ms": statistics.median(ms), "seen": len(ms)}
         for (section, name), ms in costs.items()),
        key=lambda item: item["ms"], reverse=True)[:20]
    (label_dir / "summary.json").write_text(json.dumps(result, ensure_ascii=False, indent=1))
    return result


# Allowed worsening of a median before compare fails.
TOLERANCE = {
    "first_smart_mode_ms": ("relative", 0.10),
    "indexing_settled_ms": ("relative", 0.10),
    "footprint_mb": ("relative", 0.05),
    "live_heap_mb": ("relative", 0.05),
    "loaded_classes": ("relative", 0.05),
    "freezes": ("absolute", 1),
    "freeze_total_ms": ("absolute", 500),
}


STARTUP_EPS = ["applicationActivity", "postStartupActivity", "backgroundPostStartupActivity", "startupActivity"]


def static(app):
    """What the bundle registers to run at startup, counted from the descriptors without starting it."""
    app = Path(app) / "Contents"
    jars = [p for p in (app / "lib").rglob("*.jar")] + [p for p in (app / "plugins").rglob("*.jar")]
    counts = dict.fromkeys(STARTUP_EPS, 0)
    counts["preload_services"] = 0
    tag = re.compile(r"<(" + "|".join(STARTUP_EPS) + r")\b")
    for jar in jars:
        try:
            with zipfile.ZipFile(jar) as zf:
                for name in zf.namelist():
                    if name.endswith(".xml") and (name.startswith("META-INF/") or "/" not in name):
                        text = zf.read(name).decode("utf-8", "replace")
                        for m in tag.finditer(text):
                            counts[m.group(1)] += 1
                        counts["preload_services"] += len(re.findall(r'preload="(?:true|await)"', text))
        except zipfile.BadZipFile:
            continue
    size = sum(p.stat().st_size for p in app.rglob("*") if p.is_file() and not p.is_symlink())
    metrics = {
        "bundle_mb": round(size / 2**20),
        "jars": len(jars),
        "bundled_plugins": sum(1 for p in (app / "plugins").iterdir() if p.is_dir()),
        **counts,
    }
    product = json.loads((app / "Resources" / "product-info.json").read_text())
    return {"kind": "static", "version": product["version"], "build": product["buildNumber"], "metrics": {k: {"median": v, "min": v, "max": v} for k, v in metrics.items()}}


# Static counts may not grow at all; size may grow 2%.
STATIC_TOLERANCE = {"bundle_mb": ("relative", 0.02), "jars": ("absolute", 0), "bundled_plugins": ("absolute", 0),
                    "preload_services": ("absolute", 0), **{ep: ("absolute", 0) for ep in STARTUP_EPS}}


# The bundle list. A static baseline measured on an app built before its last change does not see plugins removed
# since then, so putting one back would not fail the gate.
BUNDLE_LIST = "build/src/org/jetbrains/intellij/build/IdeaCommunityProperties.kt"


def stale_static_baseline(baseline_doc):
    """Why the static baseline is older than the bundle list, or None. Dates only: the baseline's app build date
    comes from its build number 262.<YYMMDD>, the bundle list's from its last commit; a change made on the build day
    is not seen."""
    m = re.fullmatch(r"\d+\.(\d\d)(\d\d)(\d\d)", str(baseline_doc.get("build", "")))
    if not m:
        print(f"note: the baseline app's build number {baseline_doc.get('build')!r} carries no build date (not built by "
              f"phantom-build.sh?), so its age against {BUNDLE_LIST} is not checked", file=sys.stderr)
        return None
    built = f"20{m.group(1)}-{m.group(2)}-{m.group(3)}"
    repo = Path(__file__).resolve().parent
    try:
        shallow = subprocess.run(["git", "rev-parse", "--is-shallow-repository"], cwd=repo, capture_output=True,
                                 text=True, check=True).stdout.strip() == "true"
        changed = subprocess.run(["git", "log", "-1", "--format=%cs %h", "--", BUNDLE_LIST],
                                 cwd=repo, capture_output=True, text=True, check=True).stdout.split()
    except (OSError, subprocess.CalledProcessError):
        print(f"note: no git history here, so the static baseline's age against {BUNDLE_LIST} is not checked",
              file=sys.stderr)
        return None
    # In a shallow clone the oldest fetched commit stands for all history before it, so git log names it as the last
    # change to the bundle list and every baseline looks stale.
    if shallow:
        print(f"note: this is a shallow git clone, so the last change to {BUNDLE_LIST} is not known and the static "
              f"baseline's age is not checked (git fetch --unshallow to check it)", file=sys.stderr)
        return None
    if changed and changed[0] > built:
        return f"its app was built {built}, but {BUNDLE_LIST} last changed {changed[0]} ({changed[1]})"
    return None


def compare(baseline_path, summary_path):
    baseline_doc = json.loads(Path(baseline_path).read_text())
    baseline = baseline_doc["metrics"]
    current = json.loads(Path(summary_path).read_text())["metrics"]
    tolerance = STATIC_TOLERANCE if baseline_doc.get("kind") == "static" else TOLERANCE
    regressions = 0
    for metric, (kind, allowed) in tolerance.items():
        if metric not in baseline or metric not in current:
            continue
        before, after = baseline[metric]["median"], current[metric]["median"]
        limit = before * (1 + allowed) if kind == "relative" else before + allowed
        worse = after > limit
        regressions += worse
        change = f"{(after - before) / before:+.1%}" if before else f"{after - before:+}"
        print(f"{'REGRESSION' if worse else 'ok':<10} {metric:<22} {before:>10} -> {after:<10} {change}")
    stale = stale_static_baseline(baseline_doc) if baseline_doc.get("kind") == "static" else None
    if stale:
        print(f"STALE BASELINE: {baseline_path}: {stale}. Plugins removed from the bundle since then can come back "
              f"without failing this gate. Regenerate the baseline.",
              file=sys.stderr)
    return 1 if regressions else 2 if stale else 0


if __name__ == "__main__":
    command, args = sys.argv[1], sys.argv[2:]
    if command == "run":
        print(json.dumps(run(args[0]), ensure_ascii=False, indent=1))
    elif command == "show":
        show(args)
    elif command == "summary":
        summary(args[0])
    elif command == "static":
        print(json.dumps(static(args[0]), indent=1))
    elif command == "compare":
        sys.exit(compare(args[0], args[1]))
    else:
        sys.exit(f"unknown command: {command}")
