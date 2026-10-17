#!/bin/zsh
# Installs the latest Phantom IDEA build into /Applications and restarts it.
#   ./phantom-install.sh            build with phantom-build.sh, then install
#   ./phantom-install.sh --no-build install the DMG that is already built
# A running Phantom is asked to quit first (it saves its state); its open projects reopen on the next start.
set -euo pipefail
cd "$(dirname "$0")"

DMG=out/phantom-idea/artifacts/phantom-idea-261017-aarch64.dmg
APP="/Applications/Phantom IDEA.app"

if [[ "${1:-}" != "--no-build" ]]; then
  ./phantom-build.sh
fi
[[ -f "$DMG" ]] || { echo "no DMG at $DMG"; exit 1; }

was_running=false
if pgrep -f "$APP/Contents/MacOS/idea" > /dev/null; then
  was_running=true
  osascript -e 'tell application id "dev.phantom.idea" to quit' || true
  for _ in {1..60}; do pgrep -f "$APP/Contents/MacOS/idea" > /dev/null || break; sleep 1; done
  if pgrep -f "$APP/Contents/MacOS/idea" > /dev/null; then
    echo "Phantom is still running (a dialog may be asking something); close it and run again"
    exit 1
  fi
fi

mnt=$(mktemp -d)
hdiutil attach -nobrowse -readonly -mountpoint "$mnt" "$DMG" -quiet
trap 'hdiutil detach "$mnt" -quiet || true; rmdir "$mnt" 2>/dev/null || true' EXIT
rm -rf "$APP.new"
ditto "$mnt/Phantom IDEA.app" "$APP.new"
rm -rf "$APP"
mv "$APP.new" "$APP"
echo "installed $(/usr/libexec/PlistBuddy -c 'Print CFBundleVersion' "$APP/Contents/Info.plist") from $DMG ($(stat -f %Sm "$DMG"))"

if $was_running; then
  # LaunchServices may still be tearing down the old instance; try until the new one is up
  for _ in {1..5}; do
    sleep 2
    open "$APP"
    sleep 3
    pgrep -f "$APP/Contents/MacOS/idea" > /dev/null && break
  done
fi
