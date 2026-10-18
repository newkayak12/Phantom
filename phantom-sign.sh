#!/bin/zsh
# Re-signs an installed Phantom IDEA with the local "Phantom Local" certificate and a fixed identifier.
# An ad-hoc signature changes with every build, so macOS treats each build as a new app and asks for its
# privacy permissions (Downloads, Documents, ...) again; a fixed certificate + identifier keeps them.
#   ./phantom-sign.sh [/Applications/Phantom IDEA.app]
# Without the certificate in the login keychain this does nothing (the build stays ad-hoc signed).
set -euo pipefail
APP="${1:-/Applications/Phantom IDEA.app}"
ID="Phantom Local"
# the key lives in its own keychain (not login) so signing never shows a keychain password dialog
KC="$HOME/Library/Keychains/phantom-signing.keychain-db"
PASS="$HOME/.config/phantom/signing-keychain.pass"
[[ -f "$KC" && -f "$PASS" ]] && security unlock-keychain -p "$(cat "$PASS")" "$KC"
if ! security find-certificate -c "$ID" "$KC" >/dev/null 2>&1; then
  echo "no '$ID' certificate in the keychain; leaving the ad-hoc signature"
  exit 0
fi
codesign --force --sign "$ID" --identifier dev.phantom.idea "$APP"
codesign -dr - "$APP" 2>&1 | grep designated
