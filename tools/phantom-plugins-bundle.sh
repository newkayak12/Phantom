#!/bin/bash
# Publishes the Phantom-plugins plugins that Phantom bundles as a release of newkayak12/phantom-libs and points
# build/phantom-plugins-bundle.txt at it, so phantom-build.sh can download them instead of needing a Phantom-plugins
# checkout. Run it after a bundled plugin changes in Phantom-plugins, then commit build/phantom-plugins-bundle.txt.
#   PHANTOM_PLUGINS_REPO=../Phantom-plugins tools/phantom-plugins-bundle.sh
# The checkout must be clean and on the commit to publish; the release tag is phantom-plugins-<its short hash>.
# Needs gh logged in with write access to phantom-libs (or GH_TOKEN).
set -euo pipefail
root="$(cd "$(dirname "$0")/.."; pwd)"
plugins_repo="${PHANTOM_PLUGINS_REPO:?set PHANTOM_PLUGINS_REPO to the Phantom-plugins checkout}"
[[ -z "$(git -C "$plugins_repo" status --porcelain)" ]] || { echo "$plugins_repo has uncommitted changes"; exit 1; }
commit="$(git -C "$plugins_repo" rev-parse HEAD)"
tag="phantom-plugins-${commit:0:10}"
# the list phantom-build.sh bundles
eval "$(grep -E '^bundled_from_plugins=\(' "$root/phantom-build.sh")"

tasks=(); for p in "${bundled_from_plugins[@]}"; do tasks+=(":$p:buildPlugin"); done
(cd "$plugins_repo" && CI=true ./gradlew --no-configuration-cache -q -PplatformFromRepository=true "${tasks[@]}")
zips=()
for p in "${bundled_from_plugins[@]}"; do
  version="$(sed -nE 's/^version = "([^"]+)"/\1/p' "$plugins_repo/$p/build.gradle.kts")"
  zips+=("$plugins_repo/$p/build/distributions/$p-$version.zip")
done

lock="$root/build/phantom-plugins-bundle.txt"
{
  echo "# Phantom-plugins zips that phantom-build.sh bundles: release assets of newkayak12/phantom-libs, written by"
  echo "# tools/phantom-plugins-bundle.sh from Phantom-plugins $commit"
  echo "tag $tag"
  for zip in "${zips[@]}"; do (cd "$(dirname "$zip")" && shasum -a 256 "$(basename "$zip")"); done
} > "$lock.new"

gh release create "$tag" -R newkayak12/phantom-libs --title "$tag" "${zips[@]}" --notes \
  "Phantom-plugins plugins bundled by Phantom, built unchanged from newkayak12/Phantom-plugins@$commit (\`-PplatformFromRepository=true\`). sha256 in Phantom's build/phantom-plugins-bundle.txt."
mv "$lock.new" "$lock"
echo "published $tag (${#zips[@]} zips); commit build/phantom-plugins-bundle.txt"
