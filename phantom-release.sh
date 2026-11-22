#!/bin/bash
# Builds Phantom and publishes the Apple silicon DMG and the Windows x64 installer and zip as a GitHub release
# of this repository.
#   ./phantom-release.sh               build with phantom-build.sh, check the app, release
#   ./phantom-release.sh --no-build    release the DMG (and Windows files, if built) phantom-build.sh produced last
#   NOTES="..." ./phantom-release.sh   release notes (default: the commits since the previous release tag)
# Kotlin and Maven are not bundled: the build (PHANTOM_PUBLISH_PLUGINS=1) zips them for this platform build, and the release publishes each to the
# Phantom plugins repository (the Phantom-plugins checkout next to this one, or PHANTOM_PLUGINS_REPO) as release
# kotlin-262.<YYMMDD> / maven-262.<YYMMDD>, and points its marketplace.json entry there.
# Tag: v<YY>.<MM>.<DD>.<N> for build PH-<YYMMDD>.<N>. The DMG's app is ad-hoc signed; phantom-install.sh re-signs it
# with the local "Phantom Local" certificate on the machine that installs it. The Windows files are unsigned, so
# SmartScreen warns on the first run.
set -euo pipefail
root="$(cd "$(dirname "$0")"; pwd)"
cd "$root"

if [[ "${1:-}" != "--no-build" ]]; then
  # N continues after today's released builds, so a release never reuses a number (local and CI builds alike)
  if [[ -z "${PHANTOM_BUILD_SEQ:-}" ]]; then
    today="$(date +%y.%m.%d)"
    last="$(git ls-remote --tags origin "refs/tags/v$today.*" | sed -E 's#.*refs/tags/v[0-9.]+\.([0-9]+)$#\1#' | sort -n | tail -1)"
    export PHANTOM_BUILD_SEQ=$(( ${last:-0} + 1 ))
  fi
  PHANTOM_PUBLISH_PLUGINS=1 ./phantom-build.sh
fi
build="$(cat out/phantom-build-number)"          # PH-261018.2
day="${build#PH-}"; day="${day%%.*}"; n="${build##*.}"
version="${day:0:2}.${day:2:2}.${day:4:2}"
tag="v$version.$n"
dmg="out/phantom-idea/artifacts/phantom-$day.$n-aarch64.dmg"
[[ -f "$dmg" ]] || { echo "no $dmg"; exit 1; }

# The DMG must hold an intact app that reports this build
mnt="$(mktemp -d)"
hdiutil attach -nobrowse -readonly -mountpoint "$mnt" "$dmg" -quiet
trap 'hdiutil detach "$mnt" -quiet || true; rmdir "$mnt" 2>/dev/null || true' EXIT
app="$mnt/Phantom.app"
# the build signs only the launcher, ad hoc and without sealed resources, so a strict `codesign --verify` cannot pass;
# require that signature and a launcher macOS will start
codesign -d "$app/Contents/MacOS/phantom" 2>/dev/null
file "$app/Contents/MacOS/phantom" | grep -q "arm64"
shown="$(/usr/libexec/PlistBuddy -c 'Print CFBundleVersion' "$app/Contents/Info.plist")"
[[ "$shown" == "$build" ]] || { echo "Info.plist says $shown, expected $build"; exit 1; }
hdiutil detach "$mnt" -quiet; trap - EXIT; rmdir "$mnt"

# Windows x64, when phantom-build.sh built it: an NSIS installer, and a zip whose product-info.json reports this
# version and platform build (the Windows files carry no PH- number)
assets=("$dmg")
exe="out/phantom-idea/artifacts/phantom-$day.$n.exe"
zip="out/phantom-idea/artifacts/phantom-$day.$n.win.zip"
if [[ -f "$exe" || -f "$zip" ]]; then
  file "$exe" | grep -q "Nullsoft Installer"
  info="$(unzip -p "$zip" product-info.json)"
  grep -q "\"version\": *\"$version\"" <<< "$info" && grep -q "\"buildNumber\": *\"262.$day\"" <<< "$info" \
    || { echo "$zip does not report $version (262.$day)"; exit 1; }
  assets+=("$exe" "$zip")
fi

# The published plugin zip of the build whose name starts with $1 and that holds $2 (its descriptor jar)
published_zip() {
  local z; z="$(find out/phantom-idea/artifacts -path '*-plugins/*' -name "$1-*.zip" ! -name '*.blockmap.zip' | head -1)"
  # (unzip -Z1 with a name lists just that entry, failing without it; grep -q on a pipe would break it under pipefail)
  [[ -n "$z" ]] && unzip -Z1 "$z" "$2" >/dev/null || { echo "no $1 plugin zip after the build" >&2; return 1; }
  echo "$z"
}
kotlin="$(published_zip Kotlin Kotlin/lib/kotlin-plugin.jar)"
maven="$(published_zip maven-plugin maven-plugin/lib/maven-plugin.jar)"
plugins_repo="${PHANTOM_PLUGINS_REPO:-$root/../Phantom-plugins}"
[[ -f "$plugins_repo/.phantom/marketplace.json" ]] || { echo "no Phantom-plugins checkout at $plugins_repo"; exit 1; }

if [[ -z "${NOTES:-}" ]]; then
  previous="$(git tag --list 'v[0-9]*' --sort=-creatordate | head -1)"
  NOTES="$(git log --no-merges --format='- %s' ${previous:+$previous..}HEAD | head -40)"
fi
# main takes changes only through pull requests, so a release is made from a commit already merged there
git fetch -q origin main
git merge-base --is-ancestor HEAD origin/main || { echo "HEAD is not on origin/main: merge it through a pull request first"; exit 1; }
# the tag goes up with git: creating it through the API needs the token's workflow scope, since the commit touches
# .github/workflows
git tag -f "$tag" HEAD
git push -q -f origin "refs/tags/$tag"
# this checkout is a fork of intellij-community; name the repository, or gh picks the upstream
repo="$(git remote get-url origin | sed -E 's#^(git@[^:]+:|https://github.com/)##; s#\.git$##')"
# as a draft first: the release only appears once every file (the DMG is ~800 MB) has finished uploading
gh release create "$tag" "${assets[@]}" -R "$repo" --verify-tag --draft --title "Phantom $version ($build)" --notes "$NOTES"
gh release edit "$tag" -R "$repo" --draft=false

# Kotlin and Maven for this platform build in the Phantom plugins repository: a release holding each zip, and the
# marketplace.json entry pointing at it (since this build: each is compiled against this exact platform)
# gh for the Phantom plugins repository: CI passes a token scoped to it in PHANTOM_PLUGINS_TOKEN (its GH_TOKEN only
# reaches this repository); locally gh's own login reaches both
pgh() { if [[ -n "${PHANTOM_PLUGINS_TOKEN:-}" ]]; then GH_TOKEN="$PHANTOM_PLUGINS_TOKEN" gh "$@"; else gh "$@"; fi; }
prepo="$(git -C "$plugins_repo" remote get-url origin | sed -E 's#^(git@[^:]+:|https://github.com/)##; s#\.git$##')"
# main there takes changes only through pull requests: the entries are committed on a release branch in a worktree of
# its own (the checkout in $plugins_repo is left as it is), then merged by a pull request below
git -C "$plugins_repo" fetch -q origin main
pbranch="release/phantom-$version"
pwork="$(mktemp -d)/phantom-plugins"
git -C "$plugins_repo" worktree add -q -B "$pbranch" "$pwork" origin/main

# $1 zip, $2 descriptor jar in it, $3 tag prefix and asset name, $4 title, then the entry's JSON fields (ENTRY)
publish() {
  local zip="$1" jar="$2" name="$3" title="$4"
  local tag="$(echo "$name" | tr '[:upper:]' '[:lower:]')-262.$day" asset="$name-262.$day.zip" tmp pversion
  tmp="$(mktemp)"; unzip -p "$zip" "$jar" > "$tmp"
  pversion="$(unzip -p "$tmp" META-INF/plugin.xml | awk -F'[<>]' '$2 == "version" && !v { v = $3 } END { print v }')"
  rm -f "$tmp"
  [[ -n "$pversion" ]] || { echo "no version in the $title plugin.xml"; exit 1; }
  if ! pgh release view "$tag" -R "$prepo" >/dev/null 2>&1; then
    git -C "$plugins_repo" tag -f "$tag" origin/main
    git -C "$plugins_repo" push -q -f origin "refs/tags/$tag"
    cp "$zip" "out/$asset"
    pgh release create "$tag" "out/$asset" -R "$prepo" --verify-tag --title "$title $pversion for Phantom 262.$day" \
      --notes "The IntelliJ $title plugin built with Phantom $version ($build), for Phantom builds from 262.$day."
    rm -f "out/$asset"
  fi
  ENTRY="$ENTRY" VERSION="$pversion" SINCE="262.$day" DOWNLOAD="$tag/$asset" python3 - "$pwork/.phantom/marketplace.json" <<'PY'
import json, os, sys
path = sys.argv[1]
data = json.load(open(path))
entry = json.loads(os.environ["ENTRY"])
entry.update({"version": os.environ["VERSION"], "vendor": "Phantom", "since": os.environ["SINCE"], "until": "262.*",
              "download": os.environ["DOWNLOAD"]})
data["plugins"] = [p for p in data["plugins"] if p["id"] != entry["id"]] + [entry]
with open(path, "w") as f:
  json.dump(data, f, indent=2, ensure_ascii=False)
  f.write("\n")
PY
  git -C "$pwork" diff --quiet || git -C "$pwork" commit -q -am "$title $pversion for Phantom 262.$day"
}

ENTRY='{"id": "org.jetbrains.kotlin", "name": "Kotlin Support", "category": "Languages",
  "description": "Kotlin language support: editing, inspections, refactorings, debugging and Gradle Kotlin DSL scripts. IntelliJ'"'"'s Kotlin plugin (Apache 2.0) built with each Phantom release; not bundled, since Java-only projects never use it. org.jetbrains.kotlin stays its id, so plugins depending on Kotlin keep working. Built by Phantom from IntelliJ Community sources; not published by JetBrains.",
  "depends": ["com.intellij.modules.java"],
  "detect": ["org.jetbrains.kotlin:kotlin-stdlib"], "detectFiles": ["build.gradle.kts", "settings.gradle.kts"]}' \
  publish "$kotlin" Kotlin/lib/kotlin-plugin.jar Kotlin Kotlin
ENTRY='{"id": "org.jetbrains.idea.maven", "name": "Maven Support", "category": "Build Tools",
  "description": "Apache Maven projects: import and sync of pom.xml, the Maven tool window, running goals, pom.xml editing and completion, repositories and indexes. IntelliJ'"'"'s Maven plugin (Apache 2.0) built with each Phantom release; not bundled, since Gradle projects never use it. org.jetbrains.idea.maven stays its id, so plugins depending on Maven keep working. Built by Phantom from IntelliJ Community sources; not published by JetBrains.",
  "depends": ["com.intellij.modules.java"], "detectFiles": ["pom.xml"]}' \
  publish "$maven" maven-plugin/lib/maven-plugin.jar Maven Maven

# --admin merges without waiting for the pull request checks (the ruleset lets admins bypass them in pull requests only)
if [[ -n "$(git -C "$pwork" log --oneline origin/main..HEAD)" ]]; then
  git -C "$pwork" push -q -f origin "HEAD:refs/heads/$pbranch"
  pgh pr create -R "$prepo" --base main --head "$pbranch" --title "Kotlin and Maven for Phantom 262.$day" \
    --body "marketplace.json entries for the Kotlin and Maven releases built with Phantom $version ($build)."
  pgh pr merge "$pbranch" -R "$prepo" --squash --admin --delete-branch
fi
git -C "$plugins_repo" worktree remove --force "$pwork"
git -C "$plugins_repo" branch -q -D "$pbranch" 2>/dev/null || true
