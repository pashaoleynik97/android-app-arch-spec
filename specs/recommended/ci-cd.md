# Recommended: ci-cd (CI)

The rules are in `../80-ci-cd.md` (CI-01 … CI-15); this page decides whether to install the component and
what to adapt.

## When to use

- The repository is on **GitHub** and more than one person (or QA) needs installable builds.
- You want a version that moves with every merge, and a release that is one click plus a tag.

## When not to

- Another CI system (GitLab, Bitrise, Jenkins): keep the rules (they are portable) but write the pipelines
  yourself; the shipped workflows are GitHub Actions only.
- A solo prototype with no testers: the PR gate alone is cheap — install the component and delete
  `publish.yml` / `release*.yml` if you like, or skip it.
- Store-first distribution (Play internal testing): the component still helps, but add the upload step.

## What it adds

`.github/workflows/{pr-build,publish,release-trigger,release}.yml`, `.github/pull_request_template.md`,
`ci_runbook.md`, `docs/qa-installing-builds.md`, and the skill reference
`application-architecture/references/ci-cd.md`. It relies on the Core build contract from `foundation`
(BUILD-17 … BUILD-21): `version.properties`, `-PreleaseBuild=true`, the four signing env vars with debug-key
fallback, and — for bot mode `none` — `-PversionCodeOverride`.

## Choices at install

| Parameter | Choose | Safe placeholder |
|---|---|---|
| `DEFAULT_BRANCH` | the protected integration branch | `main` |
| `CI_BOT_PUSH` | `github-app` if you can create a GitHub App on the owning account; else `none` | `none` |
| `KEYSTORE_FILE` | release keystore file name at the repo root | `{{PROJECT_SLUG}}-release.jks` |
| `JDK_CI` | from `toolchain.md` | known-good value |

`none` needs no secrets at all and can be switched to `github-app` later by setting the repository variable
`CI_BOT_PUSH` — no file edit.

## Adapting this to your app

- **Example-only:** artifact names, the 90-day retention, `ubuntu-latest`, the 5 × 3 s retry, the bot commit
  wording. Change freely.
- **Load-bearing:** `[skip ci]` + the release-tip reword, the explicit `--notes-start-tag`, the serialized
  Publish group, signing exports only when secrets exist. Removing one of these reintroduces a known failure
  (see the *why* of CI-09 … CI-12).
- Store upload, Slack notifications, lint / static analysis: add steps, keep the four-workflow shape.
