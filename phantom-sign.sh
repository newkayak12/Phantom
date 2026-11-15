#!/bin/zsh
# Re-signs an installed Phantom IDEA with the local "Phantom Local" certificate and a fixed identifier.
# An ad-hoc signature changes with every build, so macOS treats each build as a new app and asks for its
# privacy permissions (Downloads, Documents, ...) again; a fixed certificate + identifier keeps them.
#   ./phantom-sign.sh [/Applications/Phantom IDEA.app]
# The certificate is looked up only in the dedicated keychain ~/Library/Keychains/phantom-signing.keychain-db, never
# in the login keychain. Without it the app is not re-signed (it keeps its ad-hoc signature) and the script fails:
# the reason goes to stderr and the exit code is 1.
# PHANTOM_SIGN_OPTIONAL=1 makes a missing certificate a warning on stderr and exit 0 instead. phantom-install.sh sets
# it, so installing on a machine without the certificate still finishes, with an ad-hoc signed app.
set -euo pipefail
APP="${1:-/Applications/Phantom IDEA.app}"
ID="Phantom Local"
# the key lives in its own keychain (not login) so signing never shows a keychain password dialog
KC="$HOME/Library/Keychains/phantom-signing.keychain-db"
PASS="$HOME/.config/phantom/signing-keychain.pass"
[[ -f "$KC" && -f "$PASS" ]] && security unlock-keychain -p "$(cat "$PASS")" "$KC"
if ! security find-certificate -c "$ID" "$KC" >/dev/null 2>&1; then
  if [[ "${PHANTOM_SIGN_OPTIONAL:-0}" == 1 ]]; then
    echo "warning: no '$ID' certificate in $KC; $APP keeps its ad-hoc signature, so macOS will ask for its privacy permissions again" >&2
    exit 0
  fi
  echo "error: no '$ID' certificate in $KC; $APP was NOT re-signed (it keeps its ad-hoc signature)." >&2
  echo "       Create the certificate in that keychain, or set PHANTOM_SIGN_OPTIONAL=1 to accept the ad-hoc signature." >&2
  exit 1
fi
codesign --force --sign "$ID" --identifier dev.phantom.idea "$APP"
codesign -dr - "$APP" 2>&1 | grep designated
