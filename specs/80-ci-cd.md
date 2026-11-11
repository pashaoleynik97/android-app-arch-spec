# 80 — CI/CD: pipelines, version movement, release distribution (CI)

Tier: **Recommended** (component `ci-cd`). Rules use SHOULD / MAY; each has a *why*. The build-side half
(`version.properties`, derived `versionCode`, `-test` suffix, signing resolution) is Core and lives in
`20-build-system.md` (BUILD-17 … BUILD-21); this file is what the pipelines do with it.
Placeholders: `{{DEFAULT_BRANCH}}`, `{{JDK_CI}}`, `{{KEYSTORE_FILE}}`, `{{PROJECT_SLUG}}`, `{{CI_BOT_PUSH}}`.
When to install it, and what to adapt: `recommended/ci-cd.md`.

Contents: 1. Topology · 2. Gates · 3. Versioning in CI · 4. Signing in CI · 5. Pushing to a protected branch ·
6. Releases · 7. Secrets and setup · 8. Process

---

## 1. Topology

**CI-01** — The project SHOULD run exactly four GitHub Actions workflows:

| Workflow | Trigger | Concurrency | Output |
|---|---|---|---|
| `pr-build.yml` | `pull_request` → `{{DEFAULT_BRANCH}}` | `pr-<ref>`, cancel in progress | status check |
| `publish.yml` | `push` → `{{DEFAULT_BRANCH}}` | one group, **not** cancelled | run artifact: release + debug APK, `-test` |
| `release-trigger.yml` | `workflow_dispatch`, boolean inputs `major` / `minor` / `patch` | one group, not cancelled | branch `release/<X.Y.Z>` |
| `release.yml` | `push` → `release/**` (+ `workflow_dispatch`, guarded to `release/` refs) | `release-<ref>`, cancel in progress | run artifact + GitHub Release `v<X.Y.Z>` with the APK |

*Why:* each event has one obvious owner; a merge always yields an installable test build, and a release is a
deliberate, reproducible act with a branch and a tag to return to for a hotfix.

## 2. Gates

**CI-02** — Pipelines SHOULD run JVM unit tests as `testDebugUnitTest`, not `test`, and the PR gate SHOULD also
run `:app:assembleDebug` and the code-quality gates `spotlessCheck checkModuleGraph :app:lintDebug` (BUILD-24..26),
keeping the lint reports as an artifact when the job fails.
*Why:* `test` runs every unit test once per build type for no extra signal (twice the time); the assemble
catches manifest-merge and resource errors that unit tests never compile; formatting, module direction and lint
are only rules if the merge gate enforces them.

**CI-03** — Instrumented tests SHOULD NOT run on CI by default; they stay few and run locally.
*Why:* emulators on hosted runners are slow and flaky; the architecture keeps logic in JVM-testable classes
(TEST rules), so the instrumented set is small enough to run by hand.

**CI-04** — Jobs SHOULD use `ubuntu-latest`, `actions/setup-java` (temurin, `{{JDK_CI}}` from
`toolchain.md`), `gradle/actions/setup-gradle` for caching, actions pinned to a major version, and
`--console=plain` on every Gradle call.
*Why:* the Android Gradle Plugin needs a newer JDK to *run* than the bytecode target; plain console output is
readable in logs; pinned majors change only when someone decides to.

**CI-05** — When the skills tooling exists (`tools/skills/lint.sh`, `check-refs.sh`), the PR gate SHOULD run
it, and the repository SHOULD carry a pull-request template with checkboxes for tests, skills and `.docs/`.
*Why:* skills that silently drift from the code teach the next agent the wrong rule; a checkbox makes "update
the skill in the same change" a reviewed step (AI / SKILL rules).

## 3. Versioning in CI

**CI-06** — In bot mode `github-app`, `publish.yml` SHOULD bump PATCH on every merge, **before** building,
inside a retry loop that on every attempt re-fetches the default branch, resets to `origin/<default>`,
recomputes the next version from `version.properties`, commits and pushes (5 attempts, 3 s apart). The
workflow's concurrency group SHOULD serialize runs.
*Why:* QA needs a version per merge; serializing prevents two runs computing the same number, and recomputing
from the latest tip keeps `versionCode` monotonic and makes `version.properties` merge conflicts impossible.
Building after the bump makes the APK carry the new version (a failed build spends a number — accepted).

**CI-07** — `release-trigger.yml` SHOULD apply the ticked segments in order with reset semantics
(`major`: X+1.0.0, `minor`: x.Y+1.0, `patch`: x.y.Z+1; none ticked = release the current version), refuse if
`release/<new>` already exists, and not retry a rejected push.
*Why:* checkboxes are a hard-to-misuse UI for semver; a manual run that raced another push is best re-run
deliberately rather than silently re-based.

**CI-08** — In bot mode `none`, no workflow SHOULD push to the default branch; builds SHOULD pass
`-PversionCodeOverride=<git rev-list --count HEAD>`, and Trigger Release SHOULD take the highest of
`version.properties`, existing `release/*` branches and `v*` tags as its base, commit the bump on the release
branch only, and dispatch `release.yml` explicitly.
*Why:* a project without a bot account still gets monotonic version codes and forward-moving releases, and
the workflow-token push of the release branch would otherwise never start the Release Build.

## 4. Signing in CI

**CI-09** — CI SHOULD decode `KEYSTORE_BASE64` into `$RUNNER_TEMP` (never the workspace) and export
`KEYSTORE_FILE` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` **only when the secrets exist** — all four
or none, failing loudly on a partial set. With none, builds SHOULD still succeed (debug-key fallback,
BUILD-21) and the Release Build SHOULD warn that the APK is debug-signed.
*Why:* an exported-but-empty variable reads as "configured" to Gradle and breaks the build; the fallback keeps
a fresh repository green until signing is set up (`ci.signing` deferred).

## 5. Pushing to a protected branch

**CI-10** — Version-bump commits SHOULD be pushed by a **GitHub App** on the default branch's ruleset bypass
list (bot mode `github-app`), with the App token used as the checkout credential. The mode SHOULD be
switchable without editing workflows (repository variable `CI_BOT_PUSH`, falling back to the rendered
`{{CI_BOT_PUSH}}`), and a `github-app` mode without `APP_ID` SHOULD fail with an actionable message.
*Why:* the workflow token cannot bypass a ruleset and its pushes never start other workflows; a personal
token ties releases to a person. App tokens are short-lived, per run, and do trigger workflows.

**CI-11** — Every bot commit SHOULD carry `[skip ci]`, and Trigger Release SHOULD reword a release-branch tip
that carries a skip directive to `chore(release): release X.Y.Z` before pushing.
*Why:* `[skip ci]` stops the bump from re-triggering Publish; the same directive on the release tip would
also skip the Release Build. Consequence, accepted: the release commit never exists on the default branch.

## 6. Releases

**CI-12** — `release.yml` SHOULD compute the previous `vX.Y.Z` tag explicitly (`git ls-remote --tags`, semver
sort) and pass it as `--notes-start-tag`; with no previous tag it uses GitHub's default range.
*Why:* release tags sit on reworded commits that are never ancestors of each other, so GitHub's automatic
pick finds no previous release and lists the whole history.

**CI-13** — Publishing SHOULD be idempotent (existing release → replace the asset with `--clobber`, notes
untouched) and assets SHOULD be self-describing (`{{PROJECT_SLUG}}-<version>.apk`; artifacts
`{{PROJECT_SLUG}}-<version>-test`, `{{PROJECT_SLUG}}-release-<version>`).
*Why:* a hotfix pushed to `release/X.Y.Z` must update the same release, and a downloaded file must say what
it is.

## 7. Secrets and setup

**CI-14** — Secrets (`APP_ID`, `APP_PRIVATE_KEY`, `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
`KEY_PASSWORD`) live only in repository secrets; keystores and `keystore.properties` are never committed
(`{{KEYSTORE_FILE}}` is gitignored). Every workflow SHOULD run green with **no** secrets in bot mode `none`.
The installer MUST NOT create the App, secrets or rulesets; it lists them for the user.
*Why:* a forgiving install never blocks on credentials, and nothing with release authority is created by an
agent.

**CI-15** — The repository SHOULD commit `ci_runbook.md` (App creation, ruleset bypass, secrets table,
end-to-end check, troubleshooting) and `docs/qa-installing-builds.md` (download from Actions / Releases, ADB
install, debug vs release share one application id → uninstall before switching).
*Why:* the one-time setup is done rarely and by different people; the QA guide removes "which APK, and why
does it not install" questions.

## 8. Process

Changing CI follows the normal task workflow (`90-ai-workflow.md`): analysis of the current state → decisions
with "why not X" → plan (Gradle plumbing first and verified locally, then the simplest workflow, then secrets)
→ verification on a throwaway PR. The `application-architecture` skill's `references/ci-cd.md` is updated in
the same change.
