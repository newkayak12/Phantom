# Working on Phantom with Claude or Codex

[AGENTS.md](../AGENTS.md) owns repository rules; [CLAUDE.md](../CLAUDE.md) imports them for Claude Code. This guide adds task routing without requiring an installed plugin, hook or agent team.

## Start with the change

1. Inspect `git status --short` and preserve existing work. Confirm the checkout before edits, especially in a worktree.
2. Check instructions in the affected directory before editing.
3. Identify the shipped owner: platform patches live here; most new plugins belong in Phantom-plugins. For a replacement bundled from that repository, verify its build source before editing the upstream plugin copy here.
4. For substantial work, state the intended behavior, affected files/modules and a concrete verification command. For small changes, proceed directly. Keep unresolved assumptions visible.

## Load only relevant skills

Codex discovers `.agents/skills/`; Claude Code discovers `.claude/skills/`. Read a skill when its task matches, and announce its use. Do not preload the entire catalog.

| Task | Local skill |
|---|---|
| Kotlin/Java style review | `code-style` |
| Investigating a failure | `debugging` |
| Adding tests / executing tests | `writing-tests` / `testing` |
| IntelliJ actions / UI review | `actions` / `ui-accessibility` |
| Module dependencies / remote execution | `module-dependencies` / `eel` |
| Registry flags / remote development | `registry` / `remote-dev` |

The inherited `commits` and `safe-push` skills describe upstream JetBrains procedures. For this fork, follow AGENTS.md instead: plain imperative English subjects, no required YouTrack ticket or Patronus/SafePush. Commit and push only within the user's requested scope.

Optional external skills from [newkayak12/claude-skills](https://github.com/newkayak12/claude-skills) can help with planning, debugging, review and verification when installed. Check actual availability before invoking one. Do not assume its team manager, MCP servers, hooks or release conventions exist in Phantom. This guide adopts its intent-based routing and explicit verification, without adding a runtime dependency.

## Verify the result

- Code: execute affected tests with the test module and fully qualified name, as required by AGENTS.md. Use Bazel compilation when there are no applicable tests.
- Documentation only: check changed links, referenced commands and `git diff --check`; no IDE build is needed.
- Run available inspections after code changes. If a tool or build prerequisite is unavailable, record the exact limitation and what remains unverified.
- Review the complete diff for unrelated edits, update `CHANGELOG.md` under `미배포`, and report behavior changed, checks actually run and remaining limits. A proposed command is not a passing check.

SessionStart output, when supplied by the client, must be shown verbatim under AGENTS.md. No repository SessionStart hook is required by this guide.
