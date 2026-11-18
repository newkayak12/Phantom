# Phantom instructions for Claude Code

@AGENTS.md

AGENTS.md is the shared source of repository rules for Claude and Codex. Read [.ai/workflow.md](.ai/workflow.md) for task routing. Use the ijproxy tool names exposed by the current client; the names in AGENTS.md identify the same operations. Read `.ai/local.md` only when it exists.

## Git

- Work on `<type>/<topic>` branches in separate worktrees. Never switch branches, reset or clean the shared checkout. Check for an existing `phantom-build.sh` before building because builds share `out/`.
- Use plain imperative English commit subjects and explain why in substantial commit bodies. Use the author's own GitHub identity rather than a company e-mail.
- Submit changes through PRs. The prepared `.github/rulesets/main.json` uses the supplied default ruleset with zero approvals and administrator bypass. The current GitHub plan rejects live ruleset application with HTTP 403; the JSON is not enforced by its presence.
- Release only a commit already on `origin/main`: merge first, then release. `phantom-release.sh` pushes the release tag and updates Phantom-plugins' marketplace through a PR.

## For Claude

- Answer in Korean, short and direct. Code, comments, commits and documentation are English except existing Korean documents and CHANGELOG 미배포 entries.
- Use context-mode tools for long output when available. Report exact verification and state what was not run.
- Releases, tags, force pushes, keychain token access and repository setting changes require explicit authorization. Honor authorization already given in the current session; do not request it again for the same authorized work.
