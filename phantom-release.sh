#!/bin/bash
# Builds Phantom IDEA and publishes the Apple silicon DMG as a GitHub release of this repository.
#   ./phantom-release.sh               build with phantom-build.sh, check the app, release
#   ./phantom-release.sh --no-build    release the DMG phantom-build.sh produced last
#   NOTES="..." ./phantom-release.sh   release notes (default: the commits since the previous release tag)
# Tag: v<YY>.<MM>.<DD>.<N> for build PH-<YYMMDD>.<N>. The DMG's app is ad-hoc signed; phantom-install.sh re-signs it
# with the local "Phantom Local" certificate on the machine that installs it.
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
repo="$(git remote get-url origin | sed -E 's#^(git@github.com:|https://github.com/)##; s#\.git$##')"
# as a draft first: the release only appears once the DMG (~800 MB) has finished uploading
gh release create "$tag" "$dmg" -R "$repo" --verify-tag --draft --title "Phantom IDEA $version ($build)" --notes "$NOTES"
gh release edit "$tag" -R "$repo" --draft=false
