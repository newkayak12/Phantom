---
project: IntelliJ IDEA Platform
languages: [Kotlin, Java]
framework: IntelliJ Platform SDK
build-system: Bazel
repository: monorepo
---

<!-- Hand-maintained in the Phantom IDEA fork. Do not run .ai/render-guides.mjs: it expects an upstream community/ layout that does not exist here, and a re-render would drop these edits unless they are first ported into .ai/guide.md and .ai/partials/. -->

**Always display the output of SessionStart hooks verbatim to the user at the start of conversation**

**Critical:** These guidelines MUST be followed at all times.

## Phantom IDEA fork

This repository is Phantom IDEA, a personal fork of JetBrains intellij-community at tag `idea/262.10968.63` (root commit `d2faebf601` is the upstream tree; every later commit is a Phantom change). The repository root IS the community root: there is no `community/` directory, so read upstream paths `community/...` as `./...`.

Read these first: [docs/phantom/README.md](docs/phantom/README.md) (index), [ARCHITECTURE.md](docs/phantom/ARCHITECTURE.md), [FORK-DELTA.md](docs/phantom/FORK-DELTA.md), [DEVELOPMENT.md](docs/phantom/DEVELOPMENT.md), [BUILD.md](docs/phantom/BUILD.md), [CHANGELOG.md](CHANGELOG.md).

Fork rules (details in DEVELOPMENT.md section 1):

- Trim the bundle by configuration (`bundledPluginModules` in `build/src/org/jetbrains/intellij/build/IdeaCommunityProperties.kt`); keep upstream sources, `.idea/modules.xml` entries and root module dependencies.
- Do not delete automatic calls to JetBrains servers by default; gate them behind `MarketplaceRequests.jetBrainsServicesEnabled` (system property `phantom.jetbrains.services`, default false).
- Keep patches to upstream code minimal and mark each one with a `// phantom-idea:` comment (`<!-- phantom-idea: -->` in XML). Many existing patches lack the marker, so find the fork delta with `git diff d2faebf601 HEAD`, not by searching markers.
- No startup activity unless needed, no EDT work or network on the startup path. New code goes in `dev.phantom.*` packages and `phantom.*` modules; new plugins belong in the separate Phantom-plugins repository by default.
- Add a line under `미배포` in [CHANGELOG.md](CHANGELOG.md) with every change. Commit subjects are plain imperative English (see DEVELOPMENT.md section 4); the upstream `commits` and `safe-push` skills (YouTrack tickets, SafePush) do not apply to this fork.

## Project Invariants

- The repository is a large monorepo with multiple IDE products and plugins.
- Module/plugin directories may contain their own AGENTS/CLAUDE instructions; follow them when present.
- `*.iml` files are the source of truth and auto-generate `BUILD.bazel` files.
- User-visible strings belong in `*.properties` for localization.

## Module-specific rules

Special handling applies to the directories below. If a file you touch lives under one of these roots, you must activate that module's rules first (read the referenced doc before edits or reviews). These rules override general guidelines if they conflict.

- **Product DSL** (`platform/build-scripts/product-dsl/`): read `platform/build-scripts/product-dsl/README.md` and `platform/build-scripts/product-dsl/docs/module-sets.md` before changing anything in this tree.

- **IJ Proxy MCP server** (`build/mcp-servers/ij-proxy/`):
  - Tests: run `bun run build` and `bun test`.
  - Bazel: do not run Bazel build and tests here.
- **PyCharm** (`./python`): use `./python/.ai/index.md`.

## Mandatory Rules

### After Code Changes

- **Run affected tests:** `./tests.cmd --module <module> --test <FQN or wildcard>` (**FQN required; simple class names do not match; always specify the test module directly**), or `node --test <file>` for `*.test.mjs`.
  `tests.cmd` performs Bazel compilation internally, so a separate `bazel build` step is not needed when tests will be run.
  Module-specific rules may override the runner. Skip if plugin has no tests. See [TESTING](./.agents/skills/testing/SKILL.md).
- **Bazel compilation without tests:** when only verifying compilation (no tests to run), use `bazel build <target>` for affected modules. Skip if only `.js`, `.mjs`, `.md`, `.txt`, or `.json` files are modified.
- After modifying `*.iml`, `BUILD.bazel`, or `.idea/` files: run `./build/jpsModelToBazelCommunityOnly.cmd` (the only generator script in this repository; `./build/jpsModelToBazel.cmd` does not exist here). It has not been confirmed to run in this fork; see DEVELOPMENT.md section 2 (c).

### After Writing Code

- Use `lint_files` to check files for warnings.
  Fix any warnings related to the code changes made. You may ignore unrelated warnings.

## Repository-wide rules

Preserve IDE-serialized .iml files in canonical form. Do not:

- add comments
- auto-format
- normalize (structure or whitespace)
- add a trailing newline at end of file
- prune (remove) empty tags
- reorder elements or attributes

## Tools (use in this order)

## Skills

Never use the `code-search` skill. See ijproxy for search tools.

### ijproxy (required)

- Read: `mcp__ijproxy__read_file`
- Edit/Write: `mcp__ijproxy__apply_patch`
- **Search symbols (preferred):** `mcp__ijproxy__search_symbol`
- Find files (glob): `mcp__ijproxy__search_file`
- Search text: `mcp__ijproxy__search_text`
- Search regex: `mcp__ijproxy__search_regex`
- List dir: `mcp__ijproxy__list_dir`

### Client fallback (no MCP)

- **No MCP:** use `./tools/fd.cmd` (file search) and `./tools/rg.cmd` (text/regex search). These are the only allowed shell file ops on repo paths.

### IDE-backed semantic tools
Available via ijproxy or JetBrains MCP. Use these for semantic operations; avoid manual search/replace when a refactor exists.

- **Default to `search_symbol` (if available) for classes/methods/fields; use `search_text`/`search_regex` mainly for strings, comments, and non-symbol matches.**
- Inspections & symbol info: `get_file_problems`, `get_symbol_info`
- Refactors: `rename` (ijproxy) / `rename_refactoring` (JetBrains MCP); use for renames and avoid manual search/replace.
- Formatting: `reformat_file`
- Concurrency checks: `find_threading_requirements_usages`, `find_lock_requirements_usages`
- Project structure & VCS: `get_project_modules`, `get_project_dependencies`, `get_repositories`, `git_status`
- Run configs: `get_run_configurations`, `execute_run_configuration`

### Tooling rules
- When ijproxy MCP is available, all repo file ops (read/search/edit/write) MUST use ijproxy tools. Do not use JetBrains MCP or generic tools.
- Fallback tools (JetBrains MCP / client) are allowed only when ijproxy is unavailable.

- For repo edits, use `mcp__ijproxy__apply_patch`. Generic `apply_patch` is forbidden unless ijproxy is unavailable.

- Never shell for file ops (`cat`, `sed`, `find`, `grep`) on repo paths, except the client fallback (`./tools/fd.cmd`, `./tools/rg.cmd`) when no MCP is available.

- Shell OK for: git (prefer `git_status` if the tool is available), build/test.

- Outside repo: native shell permitted, except for text/file search — use `./tools/rg.cmd` and `./tools/fd.cmd` (absolute paths OK) instead of native `grep`/`find`.

## Individual Preferences

`.ai/local.md` is an optional, untracked per-developer file; it is absent by default.

**Local Preferences:** @./.ai/local.md
