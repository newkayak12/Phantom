# Phantom branch rules

PR assignment is separate from branch rules: [`phantom-pr-routing.yml`](../workflows/phantom-pr-routing.yml) assigns the PR author and requests reviews from other human collaborators. Drafts wait until ready for review. The workflow runs from the default branch and never checks out PR code; it starts routing new events after merging there.

`main.json` is based on the supplied `default-ruleset.json` export (`protect-main`): changes go through a PR, zero approving reviews are required, review threads must be resolved, merge/squash/rebase are allowed, and deletion and force pushes are blocked. Repository administrators (role ID `5`) can always bypass these rules.

This file does not configure GitHub by itself. On 2026-11-17, both the rulesets and classic branch protection APIs for the private `newkayak12/Phantom` repository returned HTTP 403: `Upgrade to GitHub Pro or make this repository public to enable this feature.` No live rules or repository visibility settings were changed. This API limitation is separate from editing the exported JSON.

After the feature becomes available, list existing rulesets first:

```sh
gh api repos/newkayak12/Phantom/rulesets
```

If `protect-main` already exists, update its ID instead of creating a duplicate:

```sh
gh api --method PUT repos/newkayak12/Phantom/rulesets/ID --input .github/rulesets/main.json
```

If it does not exist, create it:

```sh
gh api --method POST repos/newkayak12/Phantom/rulesets --input .github/rulesets/main.json
```

Read the result back with `gh api repos/newkayak12/Phantom/rulesets/ID` and check the effective rules with `gh api repos/newkayak12/Phantom/rules/branches/main`. Other inherited or existing rulesets still apply. Required status checks should be added only after their job names have run successfully on a PR; the initial template does not require release jobs or unavailable hosted checks.

Schema: [GitHub repository rules REST API](https://docs.github.com/en/rest/repos/rules#create-a-repository-ruleset).
