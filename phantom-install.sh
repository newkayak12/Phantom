#!/bin/zsh
# Installs the latest Phantom build into /Applications (or $PHANTOM_APP) and restarts it.
#   ./phantom-install.sh            build with phantom-build.sh, then install
#   ./phantom-install.sh --no-build install the DMG that is already built
#   ./phantom-install.sh --release [v26.10.21.1]   install the newest GitHub release's DMG (or that tag's)
# A release is downloaded with curl, not a browser: curl sets no quarantine flag, so Gatekeeper does not stop the
# app, which is not notarized. The repository is private: the token is $GH_TOKEN, else the one gh is logged in with
# (that account must see the repository).
# A running Phantom is asked to quit first (it saves its state); its open projects reopen on the next start.
# The installed app is re-signed by phantom-sign.sh with PHANTOM_SIGN_SETUP=1 and PHANTOM_SIGN_OPTIONAL=1: on a
# machine without the "Phantom Local" certificate it first creates one (phantom-sign.sh --setup: a self-signed
# certificate in ~/Library/Keychains/phantom-signing.keychain-db, one line on stdout says so). If that fails, or with
# PHANTOM_SIGN_SETUP=0 in the environment, the app stays ad-hoc signed and a warning goes to stderr; the install does
# not fail (a certificate that is there but cannot be used, e.g. a wrong keychain password, still fails it).
# Environment: PHANTOM_APP is the app to replace (default /Applications/Phantom.app); it is also passed to
# phantom-sign.sh. It is deleted and replaced, so it must be a *.app path. Only a Phantom running from that path is
# asked to quit (by process id, not bundle id, so another copy running elsewhere is left alone), and it is restarted
# from that path.
set -euo pipefail
cd "$(dirname "$0")"

APP="${PHANTOM_APP:-/Applications/Phantom.app}"
[[ "$APP" == /*.app ]] || { echo "error: PHANTOM_APP must be an absolute path ending in .app, got '$APP'" >&2; exit 1; }
# The app was "Phantom IDEA.app" before the rename to Phantom. With the default path and no Phantom.app yet, that copy
# is the one quit and replaced: it is removed once Phantom.app is in place (settings live elsewhere and stay).
LEGACY_APP=""
if [[ -z "${PHANTOM_APP:-}" && ! -e "$APP" && -d "/Applications/Phantom IDEA.app" ]]; then
  LEGACY_APP="/Applications/Phantom IDEA.app"
fi

DMG=""
case "${1:-}" in
  --no-build) ;;
  --release)
    token="${GH_TOKEN:-$(gh auth token)}"
    repo="$(git remote get-url origin | sed -E 's#^(git@[^:]+:|https://github.com/)##; s#\.git$##')"
    api="https://api.github.com/repos/$repo/releases"
    if [[ -n "${2:-}" ]]; then which="tags/$2"; else which="latest"; fi
    release="$(curl -fsSL -H "Authorization: Bearer $token" "$api/$which")"
    i=0
    while name="$(plutil -extract "assets.$i.name" raw - <<< "$release" 2>/dev/null)"; do
      [[ "$name" == *-aarch64.dmg ]] && break
      i=$((i + 1))
    done
    [[ "$name" == *-aarch64.dmg ]] || { echo "no macOS DMG in release ${2:-latest} of $repo"; exit 1; }
    mkdir -p out/phantom-release-download
    DMG="out/phantom-release-download/$name"
    curl -fL --progress-bar -H "Authorization: Bearer $token" -H "Accept: application/octet-stream" \
      -o "$DMG" "$(plutil -extract "assets.$i.url" raw - <<< "$release")"
    find out/phantom-release-download -name '*.dmg' ! -name "$name" -delete
    ;;
  *) ./phantom-build.sh ;;
esac
# The build number in the name follows the build date, so take the newest one
[[ -n "$DMG" ]] || DMG=$(ls -t out/phantom-idea/artifacts/phantom-idea-*-aarch64.dmg 2>/dev/null | head -1)
[[ -n "$DMG" ]] || { echo "no DMG in out/phantom-idea/artifacts"; exit 1; }

was_running=false
# pgrep -f takes a regular expression and matches anywhere in the command line, so each hit is kept only when its
# command line really starts with this app's executable (a copy under another folder, or a path with ( ) in it)
# The executable was MacOS/idea before it became MacOS/phantom: the copy being replaced may still run the old one.
phantom_pids() {
  local app pid cmd apps=("$APP")
  [[ -n "$LEGACY_APP" ]] && apps+=("$LEGACY_APP")
  for pid in $(pgrep -x phantom || true) $(pgrep -x idea || true); do
    cmd="$(ps -o command= -p "$pid" 2>/dev/null)"
    for app in $apps; do
      [[ "$cmd" == "$app/Contents/MacOS/phantom"* || "$cmd" == "$app/Contents/MacOS/idea"* ]] && { print -r -- "$pid"; break; }
    done
  done
  return 0
}
pids=($(phantom_pids))
if (( ${#pids} )); then
  was_running=true
  # A quit Apple event, as from the Quit menu item, so the IDE saves its state; sent to these processes only.
  # By bundle id (dev.phantom.idea) it would go to any running copy, which need not be the one at $APP.
  for pid in $pids; do
    osascript -l JavaScript -e 'function run(argv) {
      ObjC.import("AppKit")
      const app = $.NSRunningApplication.runningApplicationWithProcessIdentifier(Number(argv[0]))
      if (!app.isNil()) app.terminate
    }' "$pid" || true
  done
  for _ in {1..180}; do [[ -n "$(phantom_pids)" ]] || break; sleep 1; done
  if [[ -n "$(phantom_pids)" ]]; then
    echo "Phantom is still running (a dialog may be asking something); close it and run again"
    exit 1
  fi
fi

mnt=$(mktemp -d)
hdiutil attach -nobrowse -readonly -mountpoint "$mnt" "$DMG" -quiet
trap 'hdiutil detach "$mnt" -quiet || true; rmdir "$mnt" 2>/dev/null || true' EXIT
rm -rf "$APP.new"
ditto "$mnt/Phantom.app" "$APP.new"
rm -rf "$APP"
mv "$APP.new" "$APP"
if [[ -n "$LEGACY_APP" ]]; then rm -rf "$LEGACY_APP"; fi
# The launcher reads the user's VM options and runtime choice from <config>/phantom.vmoptions and phantom.jdk; before
# the executable was renamed they were idea.vmoptions and idea.jdk. Copied once, so the old files keep applying.
config="$HOME/Library/Application Support/Phantom/PhantomIDEA"
for ext in vmoptions jdk; do
  if [[ -f "$config/idea.$ext" && ! -e "$config/phantom.$ext" ]]; then cp -p "$config/idea.$ext" "$config/phantom.$ext"; fi
done
# without the "Phantom Local" certificate phantom-sign.sh creates it first (PHANTOM_SIGN_SETUP=0 skips that); if there is
# still none, the app stays ad-hoc signed: phantom-sign.sh warns on stderr, the install goes on
PHANTOM_SIGN_SETUP="${PHANTOM_SIGN_SETUP:-1}" PHANTOM_SIGN_OPTIONAL=1 ./phantom-sign.sh "$APP"
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
    [[ -n "$(phantom_pids)" ]] && break
  done
fi
