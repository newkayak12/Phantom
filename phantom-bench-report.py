#!/usr/bin/env python3
"""Turns the raw outputs of phantom-bench.sh into numbers.

  phantom-bench-report.py run <run-dir>        one run's raw files -> JSON on stdout
  phantom-bench-report.py show <run.json>...   a table of runs
  phantom-bench-report.py summary <label-dir>  run-*.json -> summary.json (median, min, max per metric)
  phantom-bench-report.py compare <baseline.json> <summary.json>
                                               exit 1 when a median is worse than the baseline's beyond its tolerance
"""
import json
import re
import statistics
import sys
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

    walk(json.loads(path.read_text()), "")
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


# Allowed worsening of a median before compare fails. Calibrated in docs/phantom/bench/README.md.
TOLERANCE = {
    "first_smart_mode_ms": ("relative", 0.10),
    "indexing_settled_ms": ("relative", 0.10),
    "footprint_mb": ("relative", 0.05),
    "live_heap_mb": ("relative", 0.05),
    "loaded_classes": ("relative", 0.05),
    "freezes": ("absolute", 1),
    "freeze_total_ms": ("absolute", 500),
}


def compare(baseline_path, summary_path):
    baseline = json.loads(Path(baseline_path).read_text())["metrics"]
    current = json.loads(Path(summary_path).read_text())["metrics"]
    regressions = 0
    for metric, (kind, allowed) in TOLERANCE.items():
        if metric not in baseline or metric not in current:
            continue
        before, after = baseline[metric]["median"], current[metric]["median"]
        limit = before * (1 + allowed) if kind == "relative" else before + allowed
        worse = after > limit
        regressions += worse
        change = f"{(after - before) / before:+.1%}" if before else f"{after - before:+}"
        print(f"{'REGRESSION' if worse else 'ok':<10} {metric:<22} {before:>10} -> {after:<10} {change}")
    return 1 if regressions else 0


if __name__ == "__main__":
    command, args = sys.argv[1], sys.argv[2:]
    if command == "run":
        print(json.dumps(run(args[0]), ensure_ascii=False, indent=1))
    elif command == "show":
        show(args)
    elif command == "summary":
        summary(args[0])
    elif command == "compare":
        sys.exit(compare(args[0], args[1]))
    else:
        sys.exit(f"unknown command: {command}")
