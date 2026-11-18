# CLAUDE.md

The repository's rules are in AGENTS.md (fork rules, module rules, tools); Claude Code reads them from here.

@AGENTS.md

## Git

- `main` takes changes only through pull requests, admins included: the GitHub ruleset `.github/rulesets/main.json`
  requires a pull request, forbids force pushes and deletion, and lets admins bypass only inside a pull request.
  Apply a change to it with `gh api repos/newkayak12/Phantom/rulesets[/<id>] -X POST|PUT --input .github/rulesets/main.json`.
- Branches are `<type>/<topic>` (`fix/github-login`, `docs/phantom-dev-docs`). Commit subjects are plain English
  describing the effect (DEVELOPMENT.md section 4); the body says why.
- A release (`phantom-release.sh`) is made from a commit already on `origin/main`: merge first, then release. The
  script pushes only the release tag, and updates Phantom-plugins' marketplace.json through a pull request there.
- Commits appear under the author's own GitHub identity; no company e-mail addresses in commits.

## For Claude

- Answer the user in Korean, short and direct. Code, comments, commit messages and docs are in English, except the
  Korean documents (CHANGELOG.md's `미배포` lines, KOR files).
- Another session may be working in this same checkout: do branch work in a separate worktree
  (`git worktree add /tmp/<name> -b <branch> origin/main`), never switch branches, reset or clean in the shared one,
  and check for a running `phantom-build.sh` before building (both write `out/`).
- Long build or test output goes through the context-mode tools, not into the conversation.
- Ask before anything outward-facing or hard to undo: releases and tags, force pushes, using the Phantom keychain
  token, changing repository settings. A yes holds for that one action.
- Report what was verified and how; a step not run is said to be not run.
