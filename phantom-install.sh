#!/bin/zsh
# Installs the latest Phantom IDEA build into /Applications and restarts it.
#   ./phantom-install.sh            build with phantom-build.sh, then install
#   ./phantom-install.sh --no-build install the DMG that is already built
# A running Phantom is asked to quit first (it saves its state); its open projects reopen on the next start.
set -euo pipefail
cd "$(dirname "$0")"

APP="/Applications/Phantom IDEA.app"

if [[ "${1:-}" != "--no-build" ]]; then
  ./phantom-build.sh
fi
# The build number in the name follows the build date, so take the newest one
DMG=$(ls -t out/phantom-idea/artifacts/phantom-idea-*-aarch64.dmg 2>/dev/null | head -1)
[[ -n "$DMG" ]] || { echo "no DMG in out/phantom-idea/artifacts"; exit 1; }

was_running=false
if pgrep -f "$APP/Contents/MacOS/idea" > /dev/null; then
  was_running=true
  osascript -e 'tell application id "dev.phantom.idea" to quit' || true
  for _ in {1..180}; do pgrep -f "$APP/Contents/MacOS/idea" > /dev/null || break; sleep 1; done
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
./phantom-sign.sh "$APP"
echo "installed $(/usr/libexec/PlistBuddy -c 'Print CFBundleVersion' "$APP/Contents/Info.plist") from $DMG ($(stat -f %Sm "$DMG"))"

if $was_running; then
  # start outside ~/Downloads so the JVM does not open its working directory there (a privacy prompt after re-signing)
  cd /
  # LaunchServices may still be tearing down the old instance; try until the new one is up
  for _ in {1..5}; do
    sleep 2
    # the user's own temp dir, not the one of whatever shell ran this script (it may be a sandbox that is gone later)
    TMPDIR="$(getconf DARWIN_USER_TEMP_DIR)" open "$APP"
    sleep 3
    pgrep -f "$APP/Contents/MacOS/idea" > /dev/null && break
  done
fi
