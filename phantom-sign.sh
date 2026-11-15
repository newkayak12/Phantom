#!/bin/zsh
# Re-signs an installed Phantom IDEA with the local "Phantom Local" certificate and a fixed identifier.
# An ad-hoc signature changes with every build, so macOS treats each build as a new app and asks for its
# privacy permissions (Downloads, Documents, ...) again; a fixed certificate + identifier keeps them.
#   ./phantom-sign.sh [APP]    re-sign APP
#   ./phantom-sign.sh --setup  create the certificate on this machine (once), then exit
# APP is the first argument, else $PHANTOM_APP, else /Applications/Phantom IDEA.app.
# The certificate is self-signed and made on this machine (not an Apple certificate, no Apple account). It lives only
# in the dedicated keychain ~/Library/Keychains/phantom-signing.keychain-db, whose password is in
# ~/.config/phantom/signing-keychain.pass. codesign is pointed at that keychain with --keychain, so the keychain is
# never added to the keychain search list, the certificate is never marked trusted, and the login keychain is not used.
# --setup makes the keychain (random password, no auto-lock), the password file (600, in a 700 folder) and the
# certificate (RSA 2048, extended key usage codeSigning, 10 years) with /usr/bin/openssl and security only, without
# any dialog; the private key exists outside the keychain only in a temporary folder that is removed on exit.
# It is idempotent: with the certificate already there it says so and exits 0. It never overwrites or deletes an
# existing keychain, certificate or password file (an existing password file is reused; an existing keychain without
# its password file is an error). To remove it all: security delete-keychain <keychain>; rm <password file>.
# Without the certificate (or with its keychain but no password file) the app is not re-signed (it keeps its ad-hoc
# signature) and the script fails: the reason goes to stderr and the exit code is 1. A password file that does not
# unlock the keychain always fails with exit code 1.
# PHANTOM_SIGN_SETUP=1 runs --setup first when the certificate is missing (it says on stdout what it creates and where).
# PHANTOM_SIGN_OPTIONAL=1 makes a missing certificate, including one whose setup failed, a warning on stderr and
# exit 0 instead. phantom-install.sh sets both (PHANTOM_SIGN_SETUP=0 in its environment turns the setup off), so
# installing on a machine without the certificate creates it, and if that fails the install still finishes with an
# ad-hoc signed app.
set -euo pipefail
SELF="${0:A}"
ID="Phantom Local"
# the key lives in its own keychain (not login) so signing never shows a keychain password dialog
KC="$HOME/Library/Keychains/phantom-signing.keychain-db"
PASS="$HOME/.config/phantom/signing-keychain.pass"
# Look a certificate up only in an unlocked keychain: a lookup in a locked one makes macOS show a password dialog.
# A keychain without its password file therefore counts as having no usable certificate. A wrong password fails here.
have_cert() {
  [[ -e "$KC" && -f "$PASS" ]] || return 1
  security unlock-keychain -p "$(cat "$PASS")" "$KC" || { echo "error: cannot unlock $KC with the password in $PASS" >&2; exit 1; }
  security find-certificate -c "$ID" "$KC" >/dev/null 2>&1
}

if [[ "${1:-}" == --setup ]]; then
  if [[ -e "$KC" && ! -f "$PASS" ]]; then
    echo "error: $KC exists but its password file $PASS does not; leaving both alone." >&2
    echo "       Restore the password file, or delete that keychain (security delete-keychain \"$KC\") and run --setup again." >&2
    exit 1
  fi
  if have_cert; then
    echo "the '$ID' certificate is already in $KC; nothing to do"
    exit 0
  fi
  echo "creating a local signing certificate '$ID' (self-signed, no Apple account) in $KC, password in $PASS"
  umask 077
  if [[ -f "$PASS" ]]; then
    pw="$(cat "$PASS")"
  else
    [[ -d "${PASS:h}" ]] || mkdir -p -m 700 "${PASS:h}"
    pw="$(/usr/bin/openssl rand -hex 32)"
    (set -o noclobber; print -r -- "$pw" > "$PASS")
  fi
  if [[ ! -e "$KC" ]]; then
    [[ -d "${KC:h}" ]] || mkdir -p "${KC:h}"
    security create-keychain -p "$pw" "$KC"
  fi
  security unlock-keychain -p "$pw" "$KC"
  # no options: no auto-lock timeout and no lock on sleep
  security set-keychain-settings "$KC"
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' EXIT
  cat > "$tmp/req.cnf" <<EOF
[req]
distinguished_name = dn
x509_extensions = ext
prompt = no
[dn]
CN = $ID
[ext]
basicConstraints = critical, CA:false
keyUsage = critical, digitalSignature
extendedKeyUsage = critical, codeSigning
subjectKeyIdentifier = hash
EOF
  /usr/bin/openssl req -x509 -newkey rsa:2048 -nodes -sha256 -days 3650 -config "$tmp/req.cnf" \
    -keyout "$tmp/key.pem" -out "$tmp/cert.pem" 2>"$tmp/openssl.log" || { cat "$tmp/openssl.log" >&2; exit 1; }
  export P12_PASS="$(/usr/bin/openssl rand -hex 16)"
  # 3DES/SHA-1 PKCS#12 is what security import reads (and what LibreSSL writes by default)
  /usr/bin/openssl pkcs12 -export -name "$ID" -inkey "$tmp/key.pem" -in "$tmp/cert.pem" \
    -keypbe PBE-SHA1-3DES -certpbe PBE-SHA1-3DES -macalg sha1 -passout env:P12_PASS -out "$tmp/id.p12"
  security import "$tmp/id.p12" -k "$KC" -f pkcs12 -P "$P12_PASS" -T /usr/bin/codesign >/dev/null
  # lets codesign use the key without asking for the keychain password
  security set-key-partition-list -S apple-tool:,apple: -s -k "$pw" "$KC" >/dev/null
  have_cert
  echo "created '$ID' ($(/usr/bin/openssl x509 -in "$tmp/cert.pem" -noout -fingerprint -sha1)), $(/usr/bin/openssl x509 -in "$tmp/cert.pem" -noout -enddate)"
  exit 0
fi

APP="${1:-${PHANTOM_APP:-/Applications/Phantom IDEA.app}}"
if ! have_cert && [[ "${PHANTOM_SIGN_SETUP:-0}" == 1 ]]; then
  /bin/zsh "$SELF" --setup || echo "warning: creating the '$ID' certificate failed (reason above)" >&2
fi
if ! have_cert; then
  if [[ "${PHANTOM_SIGN_OPTIONAL:-0}" == 1 ]]; then
    echo "warning: no '$ID' certificate in $KC; $APP keeps its ad-hoc signature, so macOS will ask for its privacy permissions again" >&2
    exit 0
  fi
  echo "error: no '$ID' certificate in $KC; $APP was NOT re-signed (it keeps its ad-hoc signature)." >&2
  echo "       Create it with ./phantom-sign.sh --setup, or set PHANTOM_SIGN_OPTIONAL=1 to accept the ad-hoc signature." >&2
  exit 1
fi
codesign --force --sign "$ID" --keychain "$KC" --identifier dev.phantom.idea "$APP"
codesign -dr - "$APP" 2>&1 | grep designated
