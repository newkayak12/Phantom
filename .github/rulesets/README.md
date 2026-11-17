# Phantom branch rules

`main.json` is the desired repository ruleset: changes go through a PR, zero approving reviews are required, review threads must be resolved, and deletion and force pushes are blocked. Repository administrators (role ID `5`) can always bypass these rules.

This file does not configure GitHub by itself. On 2026-11-17, both the rulesets and classic branch protection APIs for the private `newkayak12/Phantom` repository returned HTTP 403: `Upgrade to GitHub Pro or make this repository public to enable this feature.` No live rules were changed. Keep the repository private; enabling the feature requires an account plan that supports it.

After the feature becomes available, list existing rulesets first:

```sh
gh api repos/newkayak12/Phantom/rulesets
```

If `Phantom main` already exists, update its ID instead of creating a duplicate:

```sh
gh api --method PUT repos/newkayak12/Phantom/rulesets/ID --input .github/rulesets/main.json
```

If it does not exist, create it:

```sh
gh api --method POST repos/newkayak12/Phantom/rulesets --input .github/rulesets/main.json
```

Read the result back with `gh api repos/newkayak12/Phantom/rulesets/ID` and check the effective rules with `gh api repos/newkayak12/Phantom/rules/branches/main`. Other inherited or existing rulesets still apply. Required status checks should be added only after their job names have run successfully on a PR; the initial template does not require release jobs or unavailable hosted checks.

Schema: [GitHub repository rules REST API](https://docs.github.com/en/rest/repos/rules#create-a-repository-ruleset).
