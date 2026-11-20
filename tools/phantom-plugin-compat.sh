#!/bin/bash
# Checks that the Phantom-plugins zips Phantom bundles (build/phantom-plugins-bundle.txt) are compatible with a Phantom build.
#   tools/phantom-plugin-compat.sh [IDE]
# IDE is a Phantom.app directory, a Phantom .dmg, or omitted: then the latest Phantom release DMG is downloaded from
# newkayak12/Phantom (PHANTOM_RELEASE_TAG=v26.11.19.2 picks another release). Needs curl, python3, java 17+ and, for a
# DMG, macOS hdiutil. Steps:
#   1. download every zip of the lock from newkayak12/phantom-libs and verify its sha256
#   2. static check: since/until-build cover the IDE's buildNumber, plugin ids are unique, every mandatory dependency
#      resolves to the IDE's bundled plugins/modules (product-info.json) or another bundled zip
#   3. JetBrains' open-source IntelliJ Plugin Verifier (Apache 2.0) on all zips against the IDE; compatibility problems
#      fail the check unless listed in build/phantom-plugin-compat-ignore.txt
# Exit status is non-zero on any failure. Everything happens in a temp dir that is removed (and the DMG detached) on exit.
set -euo pipefail
root="$(cd "$(dirname "$0")/.."; pwd)"
lock="$root/build/phantom-plugins-bundle.txt"
ignore_file="$root/build/phantom-plugin-compat-ignore.txt"
verifier_version=1.411
verifier_sha256=d6cc1c1f17761bb5efff187f98b63fb792c7ef2a2fb9202bb5dbea05ebc02bfa
verifier_url="https://github.com/JetBrains/intellij-plugin-verifier/releases/download/$verifier_version/verifier-cli-$verifier_version-all.jar"

work="$(mktemp -d "${TMPDIR:-/tmp}/phantom-plugin-compat.XXXXXX")"
mount_point=""
cleanup() {
  if [[ -n "$mount_point" ]]; then hdiutil detach -quiet "$mount_point" >/dev/null 2>&1 || hdiutil detach -force -quiet "$mount_point" >/dev/null 2>&1 || true; fi
  rm -rf "$work"
}
trap cleanup EXIT
fail() { echo "error: $*" >&2; exit 1; }
sha256_of() { shasum -a 256 "$1" | cut -d' ' -f1; }

# --- the IDE ---
ide="${1:-}"
if [[ -z "$ide" ]]; then
  api="https://api.github.com/repos/newkayak12/Phantom/releases/${PHANTOM_RELEASE_TAG:+tags/$PHANTOM_RELEASE_TAG}"
  [[ -n "${PHANTOM_RELEASE_TAG:-}" ]] || api="${api}latest"
  auth=(); [[ -z "${GITHUB_TOKEN:-}" ]] || auth=(-H "Authorization: Bearer $GITHUB_TOKEN")
  curl -fsSL --retry 3 ${auth[@]+"${auth[@]}"} -o "$work/release.json" "$api"
  dmg_url="$(python3 -I - "$work/release.json" <<'PY'
import json, sys
release = json.load(open(sys.argv[1]))
print(next((a["browser_download_url"] for a in release["assets"] if a["name"].endswith(".dmg")), ""))
PY
)"
  [[ -n "$dmg_url" ]] || fail "release has no DMG asset"
  echo "Downloading $dmg_url"
  curl -fsSL --retry 3 -o "$work/phantom.dmg" "$dmg_url"
  ide="$work/phantom.dmg"
fi
if [[ "$ide" == *.dmg ]]; then
  [[ -f "$ide" ]] || fail "no such DMG: $ide"
  mount_point="$work/mnt"; mkdir -p "$mount_point"
  hdiutil attach -nobrowse -readonly -quiet -mountpoint "$mount_point" "$ide" >/dev/null
  ide="$(find "$mount_point" -maxdepth 1 -name '*.app' | head -1)"
  [[ -n "$ide" ]] || fail "no .app in the DMG"
fi
[[ -d "$ide" ]] || fail "not a directory: $ide"
ide="$(cd "$ide"; pwd)"
info="$ide/Contents/Resources/product-info.json"
[[ -f "$info" ]] || info="$ide/product-info.json"
[[ -f "$info" ]] || fail "no product-info.json in $ide"
echo "IDE: $(python3 -I -c 'import json,sys; d=json.load(open(sys.argv[1])); print(d["name"], d["version"], "build", d["buildNumber"])' "$info")"

# --- the bundled zips ---
tag="$(sed -nE 's/^tag (.+)$/\1/p' "$lock")"
[[ -n "$tag" ]] || fail "no tag line in $lock"
zips="$work/zips"; mkdir -p "$zips"
count=0
while read -r sha file; do
  curl -fsSL --retry 3 -o "$zips/$file" "https://github.com/newkayak12/phantom-libs/releases/download/$tag/$file"
  [[ "$(sha256_of "$zips/$file")" == "$sha" ]] || fail "sha256 mismatch for $file"
  count=$((count + 1))
done < <(grep -E '^[0-9a-f]{64}  [^ /]+\.zip$' "$lock")
[[ "$count" -gt 0 ]] || fail "no zips listed in $lock"
echo "Downloaded $count zips from phantom-libs release $tag (sha256 ok)"

# --- static check ---
echo "== Static check =="
python3 -I - "$info" "$zips" <<'PY'
import glob, io, json, os, re, sys, zipfile
import xml.etree.ElementTree as ET

info = json.load(open(sys.argv[1]))
build = info["buildNumber"].split("-")[-1]

def parts(version):
    return [10**9 if p == "*" else int(p) for p in version.split(".")]

def cmp(a, b):
    a, b = parts(a), parts(b)
    n = max(len(a), len(b))
    a += [0] * (n - len(a)); b += [0] * (n - len(b))
    return (a > b) - (a < b)

provided = set(info.get("bundledPlugins", [])) | set(info.get("modules", []))
provided |= {e["name"] for e in info.get("layout", []) if "name" in e}

plugins = {}
errors = []
for zpath in sorted(glob.glob(os.path.join(sys.argv[2], "*.zip"))):
    zname = os.path.basename(zpath)
    root = None
    with zipfile.ZipFile(zpath) as z:
        for n in z.namelist():
            if re.fullmatch(r"[^/]+/lib/[^/]+\.jar", n):
                with zipfile.ZipFile(io.BytesIO(z.read(n))) as jar:
                    if "META-INF/plugin.xml" in jar.namelist():
                        xml = ET.fromstring(jar.read("META-INF/plugin.xml"))
                        if root is None or xml.findtext("id"):
                            root = xml
    if root is None:
        errors.append(f"{zname}: no META-INF/plugin.xml in lib/*.jar"); continue
    pid = (root.findtext("id") or "").strip()
    if not pid:
        errors.append(f"{zname}: plugin.xml has no <id>"); continue
    if pid in plugins:
        errors.append(f"{zname}: plugin id {pid} is also used by {plugins[pid]['zip']}")
    iv = root.find("idea-version")
    since = iv.get("since-build") if iv is not None else None
    until = iv.get("until-build") if iv is not None else None
    deps = [d.text.strip() for d in root.findall("depends") if d.get("optional") != "true" and d.text]
    for dep in root.findall("dependencies/plugin"):
        deps.append(dep.get("id"))
    for dep in root.findall("dependencies/module"):
        deps.append(dep.get("name"))
    plugins[pid] = {"zip": zname, "since": since, "until": until, "deps": deps,
                    "provides": {m.get("value") for m in root.findall("module")}}

for pid in plugins:
    provided.add(pid)
    provided |= plugins[pid]["provides"]

for pid, p in sorted(plugins.items()):
    problems = []
    if not p["since"]:
        problems.append("no since-build")
    elif cmp(build, p["since"]) < 0:
        problems.append(f"since-build {p['since']} is newer than the IDE build {build}")
    if p["until"] and cmp(build, p["until"]) > 0:
        problems.append(f"until-build {p['until']} is older than the IDE build {build}")
    for dep in p["deps"]:
        if dep not in provided:
            problems.append(f"mandatory dependency {dep} is not in the IDE or another bundled zip")
    status = "ok" if not problems else "FAIL"
    print(f"  {status:4} {pid} ({p['zip']}) since={p['since']} until={p['until']}")
    errors += [f"{pid}: {m}" for m in problems]

if errors:
    print("Static check failed:"); [print("  " + e) for e in errors]
    sys.exit(1)
print(f"Static check passed for {len(plugins)} plugins against build {build}")
PY

# --- Plugin Verifier ---
echo "== Plugin Verifier $verifier_version =="
jar="$work/verifier.jar"
curl -fsSL --retry 3 -o "$jar" "$verifier_url"
[[ "$(sha256_of "$jar")" == "$verifier_sha256" ]] || fail "sha256 mismatch for the Plugin Verifier jar"
reports="$work/reports"
# The verifier wants the directory holding lib/ and plugins/ (Contents of a .app) and reads the IDE once per run, which
# takes most of a run, so several plugins are verified side by side, each with its own verifier home (no shared
# ~/.pluginVerifier). JDK classes come from the IDE's own runtime.
ide_root="$ide"; [[ ! -d "$ide/Contents/lib" ]] || ide_root="$ide/Contents"
runtime_dir=""
for jbr in "$ide_root/jbr/Contents/Home" "$ide_root/jbr"; do
  if [[ -d "$jbr/lib" ]]; then runtime_dir="$jbr"; break; fi
done
export work jar ide_root reports runtime_dir
# shellcheck disable=SC2016  # expanded by the inner shell
printf '%s\0' "$zips"/*.zip | xargs -0 -P "${PHANTOM_COMPAT_JOBS:-4}" -n1 bash -c '
  name="$(basename "$1" .zip)"
  cd "$work" && java -Dplugin.verifier.home.dir="$work/verifier-home-$name" -jar "$jar" check-plugin "$1" "$ide_root" ${runtime_dir:+-runtime-dir "$runtime_dir"} -offline \
    -verification-reports-dir "$reports/$name" > "$work/verifier-$name.log" 2>&1 || echo "$name" >> "$work/verifier-crashed"
' _
if [[ -f "$work/verifier-crashed" ]]; then
  while read -r name; do echo "--- $name"; tail -15 "$work/verifier-$name.log"; done < "$work/verifier-crashed"
  fail "the Plugin Verifier did not run to completion for: $(tr '\n' ' ' < "$work/verifier-crashed")"
fi

python3 -I - "$work" "$ignore_file" "$count" <<'PY'
import collections, glob, os, re, sys

work, ignore_file, expected = sys.argv[1], sys.argv[2], int(sys.argv[3])
ignores = []
if os.path.exists(ignore_file):
    for line in open(ignore_file):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        fields = [f.strip() for f in line.split("|", 2)]
        if len(fields) != 3 or not all(fields):
            sys.exit(f"bad line in {ignore_file} (want: plugin id | problem text | reason): {line}")
        ignores.append(fields)

# One verifier run (log) per zip. Its printed problem titles ("    #Access to unresolved class ...") are one line per
# compatibility problem; a descriptor the verifier rejects is listed under "Plugin problems:" and has no report dir.
results = collections.OrderedDict()
for log in sorted(glob.glob(os.path.join(work, "verifier-*.log"))):
    name = os.path.basename(log)[len("verifier-"):-len(".log")]
    text = open(log, errors="replace").read().splitlines()
    ids = glob.glob(os.path.join(work, "reports", name, "*", "plugins", "*"))
    pid = os.path.basename(ids[0]) if ids else name
    problems, section = [], None
    for i, line in enumerate(text):
        if re.match(r"^\S", line):
            section = "compat" if line.startswith("Compatibility problems") else None
        if section == "compat" and re.match(r"^    #", line):
            problems.append(line.strip()[1:])
        if line.strip() == "Plugin problems:":
            for p in text[i + 1:]:
                if not p.startswith("        "):
                    break
                problems.append("invalid plugin: " + p.strip())
    if not ids and not problems:
        problems.append("the Plugin Verifier produced no result for this zip")
    kept = [p for p in problems if not any(i[0] in (pid, "*") and i[1] in p for i in ignores)]
    results[pid] = (kept, len(problems) - len(kept))

if len(results) != expected:
    sys.exit(f"the Plugin Verifier reported on {len(results)} plugins, expected {expected}")
bad = 0
for pid, (kept, ignored) in sorted(results.items()):
    note = f" ({ignored} ignored)" if ignored else ""
    print(f"  {'ok' if not kept else 'PROBLEMS':8} {pid}: {len(kept)} problems{note}")
    for k in kept[:5]:
        print(f"      - {k[:200]}")
    if len(kept) > 5:
        print(f"      ... and {len(kept) - 5} more")
    bad += bool(kept)
print(f"Plugin Verifier: {len(results) - bad} ok, {bad} with problems")
sys.exit(1 if bad else 0)
PY
echo "Phantom plugin compatibility: OK"
