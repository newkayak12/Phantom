#!/bin/bash
# Build Phantom IDEA with a date-based version.
#   build number: 262.<YYMMDD>  (262 = IntelliJ platform branch, kept for plugin compatibility)
#   display version: <YY>.<MM>.<DD>, artifacts: phantom-idea-<YYMMDD>-*.dmg
set -euo pipefail
root="$(cd "$(dirname "$0")"; pwd)"
d="$(date +%y%m%d)"; yy="${d:0:2}"; mm="${d:2:2}"; dd="${d:4:2}"
info="$root/community-resources/resources/idea/IdeaApplicationInfo.xml"
sed -i '' -E "s#<version eap=\"false\" major=\"[0-9]+\" minor=\"[0-9.]+\"/>#<version eap=\"false\" major=\"$yy\" minor=\"$mm.$dd\"/>#" "$info"
echo "Phantom IDEA $yy.$mm.$dd (build 262.$d)"
exec nice -n 10 "$root/installers.cmd" -Dintellij.build.target.os=current -Dbuild.number="262.$d" "$@"
