#!/bin/bash
# Builds Phantom IDEA and publishes the Apple silicon DMG and the Windows x64 installer and zip as a GitHub release
# of this repository.
#   ./phantom-release.sh               build with phantom-build.sh, check the app, release
#   ./phantom-release.sh --no-build    release the DMG (and Windows files, if built) phantom-build.sh produced last
#   NOTES="..." ./phantom-release.sh   release notes (default: the commits since the previous release tag)
# Kotlin is not bundled: the build zips it for this platform build, and the release publishes it to the Phantom plugins
# repository (the Phantom-plugins checkout next to this one, or PHANTOM_PLUGINS_REPO) as release kotlin-262.<YYMMDD>,
# and points its marketplace.json entry there.
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
  ./phantom-build.sh
fi
build="$(cat out/phantom-build-number)"          # PH-261018.2
day="${build#PH-}"; day="${day%%.*}"; n="${build##*.}"
version="${day:0:2}.${day:2:2}.${day:4:2}"
tag="v$version.$n"
dmg="out/phantom-idea/artifacts/phantom-idea-$day.$n-aarch64.dmg"
[[ -f "$dmg" ]] || { echo "no $dmg"; exit 1; }

# The DMG must hold an intact app that reports this build
mnt="$(mktemp -d)"
hdiutil attach -nobrowse -readonly -mountpoint "$mnt" "$dmg" -quiet
trap 'hdiutil detach "$mnt" -quiet || true; rmdir "$mnt" 2>/dev/null || true' EXIT
app="$mnt/Phantom IDEA.app"
# the build signs only the launcher, ad hoc and without sealed resources, so a strict `codesign --verify` cannot pass;
# require that signature and a launcher macOS will start
codesign -d "$app/Contents/MacOS/idea" 2>/dev/null
file "$app/Contents/MacOS/idea" | grep -q "arm64"
shown="$(/usr/libexec/PlistBuddy -c 'Print CFBundleVersion' "$app/Contents/Info.plist")"
[[ "$shown" == "$build" ]] || { echo "Info.plist says $shown, expected $build"; exit 1; }
hdiutil detach "$mnt" -quiet; trap - EXIT; rmdir "$mnt"

# Windows x64, when phantom-build.sh built it: an NSIS installer, and a zip whose product-info.json reports this
# version and platform build (the Windows files carry no PH- number)
assets=("$dmg")
exe="out/phantom-idea/artifacts/phantom-idea-$day.$n.exe"
zip="out/phantom-idea/artifacts/phantom-idea-$day.$n.win.zip"
if [[ -f "$exe" || -f "$zip" ]]; then
  file "$exe" | grep -q "Nullsoft Installer"
  info="$(unzip -p "$zip" product-info.json)"
  grep -q "\"version\": *\"$version\"" <<< "$info" && grep -q "\"buildNumber\": *\"262.$day\"" <<< "$info" \
    || { echo "$zip does not report $version (262.$day)"; exit 1; }
  assets+=("$exe" "$zip")
fi

kotlin="$(find out/phantom-idea/artifacts -path '*-plugins/*' -name 'Kotlin*.zip' | head -1)"
# (unzip -Z1 with a name lists just that entry, failing without it; grep -q on a pipe would break it under pipefail)
[[ -n "$kotlin" ]] && unzip -Z1 "$kotlin" Kotlin/lib/kotlin-plugin.jar >/dev/null || { echo "no Kotlin plugin zip after the build"; exit 1; }
plugins_repo="${PHANTOM_PLUGINS_REPO:-$root/../Phantom-plugins}"
[[ -f "$plugins_repo/.phantom/marketplace.json" ]] || { echo "no Phantom-plugins checkout at $plugins_repo"; exit 1; }

if [[ -z "${NOTES:-}" ]]; then
  previous="$(git tag --list 'v[0-9]*' --sort=-creatordate | head -1)"
  NOTES="$(git log --no-merges --format='- %s' ${previous:+$previous..}HEAD | head -40)"
fi
# CI checks out a pushed commit; a local release first pushes what it built
[[ -n "${GITHUB_ACTIONS:-}" ]] || git push -q origin HEAD
# the tag goes up with git: creating it through the API needs the token's workflow scope, since the commit touches
# .github/workflows
git tag -f "$tag" HEAD
git push -q -f origin "refs/tags/$tag"
# this checkout is a fork of intellij-community; name the repository, or gh picks the upstream
repo="$(git remote get-url origin | sed -E 's#^(git@[^:]+:|https://github.com/)##; s#\.git$##')"
# as a draft first: the release only appears once every file (the DMG is ~800 MB) has finished uploading
gh release create "$tag" "${assets[@]}" -R "$repo" --verify-tag --draft --title "Phantom IDEA $version ($build)" --notes "$NOTES"
gh release edit "$tag" -R "$repo" --draft=false

# Kotlin for this platform build in the Phantom plugins repository: a release holding the zip, and the marketplace.json
# entry pointing at it (since this build: Kotlin is compiled against this exact platform)
ktag="kotlin-262.$day"
kzip="Kotlin-262.$day.zip"
kjar="$(mktemp)"; unzip -p "$kotlin" Kotlin/lib/kotlin-plugin.jar > "$kjar"
kversion="$(unzip -p "$kjar" META-INF/plugin.xml | awk -F'[<>]' '$2 == "version" && !v { v = $3 } END { print v }')"
rm -f "$kjar"
[[ -n "$kversion" ]] || { echo "no version in the Kotlin plugin.xml"; exit 1; }
prepo="$(git -C "$plugins_repo" remote get-url origin | sed -E 's#^(git@[^:]+:|https://github.com/)##; s#\.git$##')"
git -C "$plugins_repo" fetch -q origin main
git -C "$plugins_repo" checkout -q main
git -C "$plugins_repo" reset -q --hard origin/main
if ! gh release view "$ktag" -R "$prepo" >/dev/null 2>&1; then
  git -C "$plugins_repo" tag -f "$ktag" origin/main
  git -C "$plugins_repo" push -q -f origin "refs/tags/$ktag"
  cp "$kotlin" "out/$kzip"
  gh release create "$ktag" "out/$kzip" -R "$prepo" --verify-tag --title "Kotlin $kversion for Phantom 262.$day" \
    --notes "The IntelliJ Kotlin plugin built with Phantom IDEA $version ($build), for Phantom builds from 262.$day."
  rm -f "out/$kzip"
fi
KVERSION="$kversion" SINCE="262.$day" DOWNLOAD="$ktag/$kzip" python3 - "$plugins_repo/.phantom/marketplace.json" <<'PY'
import json, os, sys
path = sys.argv[1]
data = json.load(open(path))
entry = {
  "id": "org.jetbrains.kotlin", "name": "Kotlin", "version": os.environ["KVERSION"], "source": "kotlin",
  "description": "Kotlin language support: editing, inspections, refactorings, debugging and Gradle Kotlin DSL scripts. "
                 "The IntelliJ Kotlin plugin (Apache 2.0) built with Phantom; not bundled, since Java-only projects never use it.",
  "vendor": "JetBrains", "category": "Languages", "since": os.environ["SINCE"], "until": "262.*",
  "depends": ["com.intellij.modules.java"], "download": os.environ["DOWNLOAD"],
  "detect": ["org.jetbrains.kotlin:kotlin-stdlib"], "detectFiles": ["build.gradle.kts", "settings.gradle.kts"],
}
plugins = [p for p in data["plugins"] if p["id"] != entry["id"]]
data["plugins"] = plugins + [entry]
with open(path, "w") as f:
  json.dump(data, f, indent=2, ensure_ascii=False)
  f.write("\n")
PY
if ! git -C "$plugins_repo" diff --quiet; then
  git -C "$plugins_repo" commit -q -am "Kotlin $kversion for Phantom 262.$day"
  git -C "$plugins_repo" push -q origin main
fi
