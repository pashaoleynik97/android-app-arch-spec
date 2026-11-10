# Component: ci-cd (Recommended)

Rules: `specs/80-ci-cd.md` (CI-01 … CI-15). When to offer it: `specs/recommended/ci-cd.md`.

## What it adds

| File | Purpose |
|---|---|
| `.github/workflows/pr-build.yml` | PR gate: `testDebugUnitTest :app:assembleDebug` + skills lint when present |
| `.github/workflows/publish.yml` | merge → tests → PATCH bump (bot mode `github-app`) → `-test` APKs as a run artifact |
| `.github/workflows/release-trigger.yml` | manual semver bump with reset → `release/<X.Y.Z>` |
| `.github/workflows/release.yml` | release branch → release APK → run artifact + GitHub Release `v<X.Y.Z>` |
| `.github/pull_request_template.md` | tests / skills / `.docs` checkboxes |
| `ci_runbook.md` | one-time setup for the user: GitHub App, secrets, ruleset, end-to-end check |
| `docs/qa-installing-builds.md` | how testers download and install builds |
| `.claude/skills/application-architecture/references/ci-cd.md` | the skill reference (index line added to `SKILL.md`) |

No Gradle module, no code. It relies on what `foundation` already ships: `version.properties`,
`keystore.properties.template`, the gitignored keystore, and `app/build.gradle.kts` reading
`-PreleaseBuild=true`, the env vars `KEYSTORE_FILE` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` with a
debug-key fallback, and (bot mode `none`) `-PversionCodeOverride=<int>`.

## Prerequisites

- `foundation` installed.
- The project is (or will be) on GitHub. `gh` is optional — only for the user's own secret setup.

## Questions (all skippable)

| Parameter | Ask | Safe placeholder |
|---|---|---|
| `DEFAULT_BRANCH` | "Which branch do PRs merge into?" (check `git symbolic-ref --short HEAD`) | `main` |
| `CI_BOT_PUSH` | "Can you create a GitHub App on the account that owns the repo, so CI may push version bumps? (`github-app` / `none`)" | `none` (deferred `ci.bot`) |
| `KEYSTORE_FILE` | "File name of the release keystore at the repo root?" | `{{PROJECT_SLUG}}-release.jks` (deferred `ci.signing`) |
| `JDK_CI` | not asked — from `specs/toolchain.md` | known-good value |

Record with `R set DEFAULT_BRANCH=… CI_BOT_PUSH=… KEYSTORE_FILE=…` (`KEY=-` for a skip).

## Install

```
R plan ci-cd
R install ci-cd
```

No manual file edits. Then **tell the user** the manual steps below — and do **none** of them yourself
(INSTALL golden rule 5; CI-14): never create the App, secrets, variables or rulesets, never generate a
keystore, never push.

Manual steps for the user (copy into the final report; details in `ci_runbook.md`):
1. Push the repository to GitHub (if not yet).
2. Bot mode `github-app` only: create the GitHub App on the owning account (Contents: Read and write, webhook
   off), install it on the repository, add secrets `APP_ID` + `APP_PRIVATE_KEY`, and put the App on the
   default-branch ruleset's bypass list. Switching modes later: repository variable `CI_BOT_PUSH`.
3. Signing (optional): keystore at the repo root as the chosen file name, local `keystore.properties` from the
   template, secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
4. Ruleset on the default branch: require a PR and the status check `Unit tests + debug build`.
5. End-to-end check: throwaway PR → merge → Trigger Release with `minor` (runbook §4).

## Gate

```
./gradlew :app:assembleDebug
```

Plus, when available: `actionlint` on `.github/workflows/` (optional — never install it without asking), and
a YAML parse, e.g. `ruby -ryaml -e 'Dir[".github/workflows/*.yml"].each { |f| YAML.load_file(f) }'`.

## Completing deferred items

- **`ci.bot`** (`CI_BOT_PUSH`): no re-render needed — the user sets the repository variable `CI_BOT_PUSH` to
  `github-app` after creating the App. To change the rendered default too: `R set CI_BOT_PUSH=github-app`,
  `R rerender CI_BOT_PUSH`.
- **`ci.signing`** (`KEYSTORE_FILE`): `R set KEYSTORE_FILE=<name>.jks`, `R rerender KEYSTORE_FILE` (updates the
  runbook, `.gitignore`, the template and `app/build.gradle.kts` where unmodified), then the user adds the
  signing secrets. Builds were green before and stay green.

## Troubleshooting

| Symptom | Fix |
|---|---|
| Workflows run against the wrong branch | `DEFAULT_BRANCH` wrong — `R set DEFAULT_BRANCH=<b>`, `R rerender DEFAULT_BRANCH` |
| Publish fails "CI_BOT_PUSH=github-app but secret APP_ID is missing" | App not set up yet: set the repository variable `CI_BOT_PUSH=none`, or finish runbook §1 |
| Signing step fails on a partial set | set all four signing secrets, or none |
| `versionCode` constant in mode `none` | `app/build.gradle.kts` does not read `versionCodeOverride` (modified locally?) — see BUILD-18 |
| More in `ci_runbook.md` → Troubleshooting | |

## Commit message

`chore(arch-spec): ci-cd [spec vX.Y.Z]`
