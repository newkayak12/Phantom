#!/bin/bash
# Build Phantom IDEA (macOS on Apple silicon, Windows x64) with a date-based version.
#   Phantom build number: PH-<YYMMDD>.<N>  (what the IDE, Info.plist and artifact names show; N counts the builds of that day)
#   display version:      <YY>.<MM>.<DD>
#   artifacts:            out/phantom-idea/artifacts/phantom-idea-<YYMMDD>.<N>-aarch64.dmg, and for Windows
#                         phantom-idea-<YYMMDD>.<N>.exe (installer) and .win.zip (only the newest build is kept)
# PHANTOM_WINDOWS=0 builds only the DMG. Windows is cross-built on this Mac: a second build of the same compiled output.
#   platform build:       262.<YYMMDD>  (internal: bundled plugins' since/until-build and the IDE's apiVersion, so plugins
#                                        built for IntelliJ Platform 262 keep loading; never shown as Phantom's version)
# PHANTOM_BUILD_SEQ=<N> fixes N (CI passes its run number); otherwise it comes from out/phantom-build-seq.
# The build number goes to out/phantom-build-number for phantom-release.sh.
set -euo pipefail
root="$(cd "$(dirname "$0")"; pwd)"
d="$(date +%y%m%d)"; yy="${d:0:2}"; mm="${d:2:2}"; dd="${d:4:2}"
info="$root/community-resources/resources/idea/IdeaApplicationInfo.xml"
sed -i '' -E "s#<version eap=\"false\" major=\"[0-9]+\" minor=\"[0-9.]+\"/>#<version eap=\"false\" major=\"$yy\" minor=\"$mm.$dd\"/>#" "$info"

mkdir -p "$root/out"
seq_file="$root/out/phantom-build-seq"
if [[ -n "${PHANTOM_BUILD_SEQ:-}" ]]; then
  n="$PHANTOM_BUILD_SEQ"
else
  last="$(cat "$seq_file" 2>/dev/null || true)"
  if [[ "${last%%.*}" == "$d" ]]; then n=$(( ${last#*.} + 1 )); else n=1; fi
fi
echo "$d.$n" > "$seq_file"
phantom="PH-$d.$n"
echo "Phantom IDEA $yy.$mm.$dd (build $phantom, platform 262.$d)"
nice -n 10 "$root/installers.cmd" -Dintellij.build.target.os=current -Dintellij.build.target.arch=aarch64 \
  -Dbuild.number="262.$d" -Dphantom.build.number="$phantom" "$@"

# Keep out/ from growing by gigabytes per build: the .sit archives and temp are intermediates, older DMGs are superseded
artifacts="$root/out/phantom-idea/artifacts"
dmg="$artifacts/phantom-idea-$d.$n-aarch64.dmg"
[[ -f "$dmg" ]] || { echo "no $dmg after the build"; exit 1; }
rm -f "$artifacts"/phantom-idea-*.sit "$artifacts"/phantom-idea-*.sit.*
find "$artifacts" -maxdepth 1 -name 'phantom-idea-*.dmg*' ! -name "phantom-idea-$d.$n-aarch64.dmg*" -delete
rm -rf "$root/out/phantom-idea/temp"
# maven-artifacts (platform libraries for publishing), jps and the dist.* trees are rebuilt every time and not shipped
rm -rf "$artifacts/maven-artifacts" "$artifacts/jps" "$root"/out/phantom-idea/dist.*

if [[ "${PHANTOM_WINDOWS:-1}" != 0 ]]; then
  # The Windows build cleans out/phantom-idea like any build (leftovers of the macOS one make it fail), so the DMG
  # waits outside it
  mv "$dmg"* "$root/out/"
  nice -n 10 "$root/installers.cmd" -Dintellij.build.target.os=windows -Dintellij.build.target.arch=x64 \
    -Dbuild.number="262.$d" -Dphantom.build.number="$phantom" "$@"
  mv "$root/out/$(basename "$dmg")"* "$artifacts/"
  ls "$artifacts/phantom-idea-$d.$n".exe "$artifacts/phantom-idea-$d.$n".win.zip >/dev/null \
    || { echo "no Windows installer or zip for $d.$n after the build"; exit 1; }
  rm -rf "$root/out/phantom-idea/temp" "$artifacts/maven-artifacts" "$artifacts/jps" "$root"/out/phantom-idea/dist.*
fi
echo "$phantom" > "$root/out/phantom-build-number"
echo "built $dmg"
