---
name: commits
description: Use when committing changes, writing or fixing a commit message, or amending a commit in the Phantom repository.
---

# Phantom commits

1. Read [AGENTS.md](../../../AGENTS.md) and [DEVELOPMENT.md section 4](../../../docs/phantom/DEVELOPMENT.md). They own this fork's commit rules.
2. Inspect staged and unstaged changes. Preserve unrelated work and stage only the requested scope. Include the relevant CHANGELOG.md entry.
3. Use a plain imperative English subject, such as `Fix plugin alias resolution`. Do not require YouTrack IDs, JetBrains prefixes, IJ-MR trailers or Patronus validation.
4. Explain motivation and meaningful verification in the body when the change needs it. Do not claim checks that did not run.
5. Commit or amend only within the user's requested scope. Do not push unless requested; consult the safe-push skill for that step.
