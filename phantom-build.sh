#!/bin/bash
# Build Phantom IDEA (macOS, Apple silicon) with a date-based version.
#   Phantom build number: PH-<YYMMDD>.<N>  (what the IDE, Info.plist and artifact names show; N counts the builds of that day)
#   display version:      <YY>.<MM>.<DD>
#   artifact:             out/phantom-idea/artifacts/phantom-idea-<YYMMDD>.<N>-aarch64.dmg (only the newest is kept)
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
echo "$phantom" > "$root/out/phantom-build-number"
echo "built $dmg"
