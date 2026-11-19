---
name: safe-push
description: Use when the user asks to push Phantom changes to a remote repository.
---

# Pushing Phantom changes

1. Read [AGENTS.md](../../../AGENTS.md). Phantom uses ordinary Git and GitHub pull requests; JetBrains Patronus/SafePush is not this fork's workflow.
2. Inspect the current branch, remotes, working tree and commits to be pushed. Preserve unrelated work and confirm the destination matches the requested repository and branch.
3. Complete affected verification, review the diff and include the CHANGELOG.md entry. State any unavailable checks accurately.
4. Push only the requested branch and scope. Do not force-push, rewrite published history or push to the default branch unless explicitly authorized for that action.
5. When a pull request is requested, provide a concise description of behavior, motivation and actual validation. Report the resulting branch or PR link.
