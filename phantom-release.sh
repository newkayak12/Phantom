#!/bin/bash
# Builds Phantom and publishes the Apple silicon DMG and the Windows x64 installer and zip as a GitHub release
# of this repository.
#   ./phantom-release.sh               build with phantom-build.sh, check the app, release
#   ./phantom-release.sh --no-build    release the DMG (and Windows files, if built) phantom-build.sh produced last
#   NOTES="..." ./phantom-release.sh   release notes (default: the commits since the previous release tag)
#   ./phantom-release.sh --next-tag    print the tag the next release gets (v<YY>.<MM>.<DD>.<N>)
#   ./phantom-release.sh --windows v<YY>.<MM>.<DD>.<N>
#                                      build the Windows installer and zip of that release from this checkout (its tag)
#                                      and add them to the release
#   ./phantom-release.sh --windows-build TAG / --windows-upload TAG
#                                      the two halves of --windows, as CI runs them: the build alongside the macOS
#                                      release (before the tag exists), the upload once that release is out
# PHANTOM_BUILD_DAY=<YYMMDD> and PHANTOM_BUILD_SEQ=<N> fix the release's number (CI passes --next-tag's to both builds).
# Kotlin and Maven are not bundled: the build (PHANTOM_PUBLISH_PLUGINS=1) zips them for this platform build, and the release publishes each to the
# Phantom plugins repository (the Phantom-plugins checkout next to this one, or PHANTOM_PLUGINS_REPO) as release
# kotlin-262.<YYMMDD> / maven-262.<YYMMDD>, and points its marketplace.json entry there. PHANTOM_RELEASE_PLUGINS=0 skips
# them (neither built nor published; the marketplace keeps the previous release's), as CI does without PHANTOM_PLUGINS_TOKEN.
# Tag: v<YY>.<MM>.<DD>.<N> for build PH-<YYMMDD>.<N>. The DMG's app is ad-hoc signed; phantom-install.sh re-signs it
# with the local "Phantom Local" certificate on the machine that installs it. The Windows files are unsigned, so
# SmartScreen warns on the first run.
set -euo pipefail
root="$(cd "$(dirname "$0")"; pwd)"
cd "$root"
# this checkout is a fork of intellij-community; name the repository, or gh picks the upstream
repo="$(git remote get-url origin | sed -E 's#^(git@[^:]+:|https://github.com/)##; s#\.git$##')"

# Windows x64 files: an NSIS installer, and a zip whose product-info.json reports version $3 and platform build 262.$4
# (the Windows files carry no PH- number)
check_windows() {
  file "$1" | grep -q "Nullsoft Installer"
  local info; info="$(unzip -p "$2" product-info.json)"
  grep -q "\"version\": *\"$3\"" <<< "$info" && grep -q "\"buildNumber\": *\"262.$4\"" <<< "$info" \
    || { echo "$2 does not report $3 (262.$4)"; exit 1; }
}

# N continues after today's released builds, so a release never reuses a number (local and CI builds alike)
next_tag() {
  local today last
  today="$(date +%y.%m.%d)"
  last="$(git ls-remote --tags origin "refs/tags/v$today.*" | sed -E 's#.*refs/tags/v[0-9.]+\.([0-9]+)$#\1#' | sort -n | tail -1)"
  echo "v$today.$(( ${last:-0} + 1 ))"
}
if [[ "${1:-}" == "--next-tag" ]]; then next_tag; exit 0; fi

case "${1:-}" in
  --windows|--windows-build|--windows-upload)
    mode="$1"
    tag="${2:?usage: $0 $1 v<YY>.<MM>.<DD>.<N>}"
    [[ "$tag" =~ ^v([0-9]{2})\.([0-9]{2})\.([0-9]{2})\.([0-9]+)$ ]] || { echo "not a release tag: $tag"; exit 1; }
    day="${BASH_REMATCH[1]}${BASH_REMATCH[2]}${BASH_REMATCH[3]}"; n="${BASH_REMATCH[4]}"
    exe="out/phantom-idea/artifacts/phantom-$day.$n.exe"
    zip="out/phantom-idea/artifacts/phantom-$day.$n.win.zip"
    if [[ "$mode" == --windows ]]; then
      [[ "$(git rev-parse HEAD)" == "$(git rev-parse "$tag^{commit}")" ]] || { echo "HEAD is not $tag"; exit 1; }
      gh release view "$tag" -R "$repo" >/dev/null
    fi
    if [[ "$mode" != --windows-upload ]]; then
      PHANTOM_MACOS=0 PHANTOM_WINDOWS=1 PHANTOM_BUILD_DAY="$day" PHANTOM_BUILD_SEQ="$n" ./phantom-build.sh
    fi
    check_windows "$exe" "$zip" "${day:0:2}.${day:2:2}.${day:4:2}" "$day"
    if [[ "$mode" != --windows-build ]]; then
      gh release upload "$tag" "$exe" "$zip" -R "$repo" --clobber
    fi
    exit 0;;
esac

if [[ "${1:-}" != "--no-build" ]]; then
  if [[ -z "${PHANTOM_BUILD_SEQ:-}" ]]; then
    tag="$(next_tag)"
    export PHANTOM_BUILD_SEQ="${tag##*.}"
  fi
  PHANTOM_PUBLISH_PLUGINS="${PHANTOM_RELEASE_PLUGINS:-1}" ./phantom-build.sh
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
# the third-party list of this build, for the Kotlin and Maven zips below (the dist trees are deleted after the build)
notices="$(mktemp -d)"
cp "$app/Contents/license/third-party-libraries.html" "$app/Contents/license/third-party-libraries.json" "$notices/"
hdiutil detach "$mnt" -quiet; trap - EXIT; rmdir "$mnt"

# Windows x64, when phantom-build.sh built it: an NSIS installer, and a zip whose product-info.json reports this
# version and platform build (the Windows files carry no PH- number)
assets=("$dmg")
exe="out/phantom-idea/artifacts/phantom-$day.$n.exe"
zip="out/phantom-idea/artifacts/phantom-$day.$n.win.zip"
if [[ -f "$exe" || -f "$zip" ]]; then
  check_windows "$exe" "$zip" "$version" "$day"
  assets+=("$exe" "$zip")
fi

# The published plugin zip of the build whose name starts with $1 and that holds $2 (its descriptor jar)
published_zip() {
  local z; z="$(find out/phantom-idea/artifacts -path '*-plugins/*' -name "$1-*.zip" ! -name '*.blockmap.zip' | head -1)"
  # (unzip -Z1 with a name lists just that entry, failing without it; grep -q on a pipe would break it under pipefail)
  [[ -n "$z" ]] && unzip -Z1 "$z" "$2" >/dev/null || { echo "no $1 plugin zip after the build" >&2; return 1; }
  echo "$z"
}
if [[ "${PHANTOM_RELEASE_PLUGINS:-1}" != 0 ]]; then
  kotlin="$(published_zip Kotlin Kotlin/lib/kotlin-plugin.jar)"
  maven="$(published_zip maven-plugin maven-plugin/lib/maven-plugin.jar)"
  plugins_repo="${PHANTOM_PLUGINS_REPO:-$root/../Phantom-plugins}"
  [[ -f "$plugins_repo/.phantom/marketplace.json" ]] || { echo "no Phantom-plugins checkout at $plugins_repo"; exit 1; }
fi

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
# the update feed: the IDE reads updates.xml of the latest release (BaseJetBrainsExternalProductResourceUrls.updateMetadataUrl)
# and offers this build to older PH builds, with a Download button that opens the release page. Windows builds carry no
# PH product code, so they find no product in it and are not offered updates.
feed="$(mktemp -d)/updates.xml"
cat > "$feed" <<EOF
<products>
  <product name="Phantom">
    <code>PH</code>
    <channel id="PH-RELEASE" name="Phantom releases" status="release" licensing="release" url="https://github.com/$repo/releases">
      <build number="$day.$n" version="$version" releaseDate="20$day">
        <message>Phantom $version is available.</message>
        <button name="Download" url="https://github.com/$repo/releases/tag/$tag" download="true"/>
      </build>
    </channel>
  </product>
</products>
EOF
assets+=("$feed")
# as a draft first: the release only appears once every file (the DMG is ~800 MB) has finished uploading
gh release create "$tag" "${assets[@]}" -R "$repo" --verify-tag --draft --title "Phantom $version ($build)" --notes "$NOTES"
gh release edit "$tag" -R "$repo" --draft=false
[[ "${PHANTOM_RELEASE_PLUGINS:-1}" != 0 ]] || { echo "Kotlin and Maven skipped (PHANTOM_RELEASE_PLUGINS=0)"; exit 0; }

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

# Copies plugin zip $1 to $2 with what Apache 2.0 section 4 asks of a work built from modified IntelliJ sources: the
# license, a NOTICE saying so, and the third-party libraries with their license texts. They go in the plugin's folder
# $3 (so an installed plugin keeps them); $4 is the plugin's title. Libraries only the plugin has are not in the IDE's
# list: build/phantom-plugin-notices/$3.txt names them and is appended to the NOTICE.
with_notices() {
  local zip="$1" out="$2" dir="$3" title="$4" stage
  stage="$(mktemp -d)"
  mkdir -p "$stage/$dir/license"
  cp LICENSE.txt "$stage/$dir/"
  cp license/*.txt "$notices"/third-party-libraries.* "$stage/$dir/license/"
  cat > "$stage/$dir/NOTICE.txt" <<NOTICE
Phantom $title plugin
Copyright 2026 Phantom contributors

This is the $title plugin of IntelliJ IDEA Community Edition (https://github.com/JetBrains/intellij-community),
built by Phantom from its sources as modified by Phantom contributors (changed files are marked with "phantom-idea:"
comments; https://github.com/newkayak12/Phantom). It is not published or endorsed by JetBrains.
This software includes code from IntelliJ IDEA
Copyright (C) JetBrains s.r.o.
https://www.jetbrains.com/idea/
Licensed under the Apache License, Version 2.0 (LICENSE.txt).

Third-party libraries in this plugin are listed, with their licenses and where to get their sources, in
license/third-party-libraries.html (and .json), the list of the whole Phantom $version build this plugin was built
with. The license/ folder holds the full texts of the licenses named there other than Apache 2.0.
NOTICE
  if [[ -f "build/phantom-plugin-notices/$dir.txt" ]]; then
    { echo; cat "build/phantom-plugin-notices/$dir.txt"; } >> "$stage/$dir/NOTICE.txt"
  fi
  cp "$zip" "$out"
  (cd "$stage" && zip -qr "$root/$out" "$dir")
  rm -rf "$stage"
}

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
    with_notices "$zip" "out/$asset" "${jar%%/*}" "$title"
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

ENTRY='{"id": "org.jetbrains.kotlin", "name": "Phantom for Kotlin", "category": "Languages",
  "description": "Kotlin language support: editing, inspections, refactorings, debugging and Gradle Kotlin DSL scripts. IntelliJ'"'"'s Kotlin plugin (Apache 2.0) built with each Phantom release; not bundled, since Java-only projects never use it. org.jetbrains.kotlin stays its id, so plugins depending on Kotlin keep working. Built by Phantom from IntelliJ Community sources; not published by JetBrains.",
  "depends": ["com.intellij.modules.java"],
  "detect": ["org.jetbrains.kotlin:kotlin-stdlib"], "detectFiles": ["build.gradle.kts", "settings.gradle.kts"]}' \
  publish "$kotlin" Kotlin/lib/kotlin-plugin.jar Kotlin Kotlin
ENTRY='{"id": "org.jetbrains.idea.maven", "name": "Phantom for Apache Maven", "category": "Build Tools",
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
