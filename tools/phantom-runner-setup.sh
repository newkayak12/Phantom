#!/bin/bash
# Registers this Mac as the self-hosted GitHub Actions runner that .github/workflows/phantom-release.yml builds on.
# Run it yourself, once; it asks before each step that changes the machine.
#   tools/phantom-runner-setup.sh            install into ~/actions-runner/phantom-idea and start it as a login agent
#   tools/phantom-runner-setup.sh --remove   stop it and unregister it from GitHub
# What it does: downloads GitHub's runner (osx-arm64) into ~/actions-runner/phantom-idea, registers it for
# newkayak12/Phantom with the labels self-hosted, macOS, ARM64 (a registration token from `gh api`, valid one
# hour), and installs a launchd agent (~/Library/LaunchAgents/actions.runner.*.plist) so it runs while you are logged
# in. Jobs then run as your user: they can read your files and use your keychain, so only this repository's own
# workflows should run on it (the repository is private; no fork can trigger it).
set -euo pipefail
repo="newkayak12/Phantom"
dir="$HOME/actions-runner/phantom-idea"
ask() { read -r -p "$1 [y/N] " a; [[ "$a" == y || "$a" == Y ]]; }

if [[ "${1:-}" == "--remove" ]]; then
  cd "$dir"
  ./svc.sh stop || true
  ./svc.sh uninstall || true
  ./config.sh remove --token "$(gh api -X POST "repos/$repo/actions/runners/remove-token" -q .token)"
  exit 0
fi

version="$(gh api repos/actions/runner/releases/latest -q .tag_name)"; version="${version#v}"
ask "Download actions runner $version into $dir?" || exit 1
mkdir -p "$dir"; cd "$dir"
curl -fsSL -o runner.tar.gz "https://github.com/actions/runner/releases/download/v$version/actions-runner-osx-arm64-$version.tar.gz"
tar xzf runner.tar.gz && rm runner.tar.gz

ask "Register it as a runner of $repo (labels self-hosted, macOS, ARM64)?" || exit 1
./config.sh --unattended --replace --url "https://github.com/$repo" --name "$(scutil --get LocalHostName)-phantom" \
  --labels self-hosted,macOS,ARM64 --work _work \
  --token "$(gh api -X POST "repos/$repo/actions/runners/registration-token" -q .token)"

ask "Start it now and at every login (launchd agent)?" || { echo "run $dir/run.sh to start it by hand"; exit 0; }
./svc.sh install
./svc.sh start
