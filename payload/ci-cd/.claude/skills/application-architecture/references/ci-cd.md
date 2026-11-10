# CI/CD — pipelines, versioning, signing (component: ci-cd)

Recommended guidance (SHOULD), from `specs/80-ci-cd.md` (`CI-*`) and the build rules it relies on
(`BUILD-17` … `BUILD-21`). One-time setup lives in `ci_runbook.md`; the QA download / install guide in
`docs/qa-installing-builds.md`.

Contents: 1. Topology · 2. Versioning · 3. Signing · 4. Bot mode · 5. The non-obvious mechanics ·
6. Changing the pipelines · 7. Troubleshooting

---

## 1. Topology (CI-01)

Four GitHub Actions workflows in `.github/workflows/`; git flow is feature branch → PR → default branch
(protected, PR-only) → `release/<X.Y.Z>` branches cut by a manual workflow.

| File | Trigger | Concurrency | Does |
|---|---|---|---|
| `pr-build.yml` | PR into the default branch | per ref, cancel in progress | `testDebugUnitTest :app:assembleDebug` + skills lint (CI-05) |
| `publish.yml` | push to the default branch | one group, **no** cancel (serialized) | tests → bump PATCH (bot mode `github-app`) → release + debug APKs `-test` → run artifact (90 days) |
| `release-trigger.yml` | manual, checkboxes major / minor / patch | one group, no cancel | semver bump with reset → cut `release/<new>` |
| `release.yml` | push to `release/**` (or dispatch) | per ref, cancel in progress | tests → `assembleRelease -PreleaseBuild=true` → run artifact + GitHub Release `v<ver>` with the APK |

- Unit tests run as `testDebugUnitTest`, not `test`: `test` runs every unit test twice (debug + release
  variants) for no extra signal (CI-02).
- Instrumented tests do not run on CI (no emulator on hosted runners is cheap enough); keep them few and run
  them locally (CI-03).
- Every Gradle call uses `--console=plain`; JDK comes from `actions/setup-java` (temurin), Gradle caching
  from `gradle/actions/setup-gradle` (CI-04).
- Distribution is GitHub only: run artifacts for test builds, Releases for release builds.

## 2. Versioning (BUILD-17 … BUILD-19, CI-06 … CI-08)

- `version.properties` holds `VERSION_NAME=MAJOR.MINOR.PATCH`. In bot mode `github-app` it is **CI-owned**:
  never edit it in a feature PR. In mode `none` it is bumped by an ordinary PR.
- `app/build.gradle.kts` reads it through `providers.fileContents` — with the configuration cache on, a
  plain file read would let a job that rewrites the file and then builds bake a stale version.
- `versionCode = (MAJOR*100_000 + MINOR*1_000 + PATCH) * 10` (major, minor ≤ 99, patch ≤ 999, trailing digit
  reserved). Monotonic as long as the version grows; nothing else to store or push.
- `versionName` is `X.Y.Z-test` for every build except the release pipeline, which passes
  `-PreleaseBuild=true`. Testers can tell a release APK from a test APK on the About screen.
- Bot mode `none`: the workflows pass `-PversionCodeOverride=<commit count>` so every merge still gets a
  higher code. `app/build.gradle.kts` must honour that property (when absent, it is ignored and the code
  stays the formula's).

Movement: PATCH auto-bumps on every merge (mode `github-app`), so QA always sees a version that tracks
development; MAJOR / MINOR / PATCH move deliberately through Trigger Release, applied in order with reset —
from `M.m.p`: major → `(M+1).0.0`, minor → `M.(m+1).0`, patch → `M.m.(p+1)`, major+minor →
`(M+1).1.0`, none ticked → unchanged.

Commit messages: bot bump `chore(release): bump version to X.Y.Z [skip ci]`; manual bump
`chore(release): set version to X.Y.Z [skip ci]`; release branch tip `chore(release): release X.Y.Z`.

## 3. Signing (BUILD-20, BUILD-21, CI-09)

- Resolution per key: gitignored `keystore.properties` → env `KEYSTORE_FILE` / `KEYSTORE_PASSWORD` /
  `KEY_ALIAS` / `KEY_PASSWORD`. All four present → **both** debug and release are signed with that key (an
  IDE build updates over a CI artifact without uninstalling). Any missing → release falls back to the debug
  key, so a fresh clone and every PR build stay green.
- CI decodes `KEYSTORE_BASE64` into `$RUNNER_TEMP/release.jks` (never the workspace) and exports the four
  variables **only when the secrets exist**. An exported-but-empty variable counts as "present" to Gradle and
  breaks the build — so the export is all-or-nothing, and a partial set fails loudly.
- Release builds signed with the debug key get a workflow warning: fine for internal QA, never for users.
- Never commit a keystore or `keystore.properties`; never generate keys on someone's behalf.

## 4. Bot mode (CI-08, CI-10)

The bump commit has to land on a protected branch. The mode is the repository variable `CI_BOT_PUSH`, falling
back to the value rendered at install time.

| | `github-app` | `none` |
|---|---|---|
| Publish | bumps PATCH, pushes with the App token | no push; artifact `<slug>-X.Y.Z-build<N>-test` |
| Trigger Release | pushes the bump to the default branch, pushes `release/<v>` (which starts the build) | bump committed on `release/<v>` only; base = max(file, release branches, `v*` tags); dispatches the Release Build explicitly |
| Needs | App + `APP_ID` / `APP_PRIVATE_KEY` + ruleset bypass | nothing |

Why an App: the workflow token cannot bypass a ruleset and its pushes **never start other workflows**
(`workflow_dispatch` is the one documented exception — which is how mode `none` starts the Release Build).
App tokens are short-lived, minted per run by `actions/create-github-app-token`, not tied to a person, can sit
on the bypass list, and do trigger workflows. The checkout MUST use the App token so `git push` uses it.

## 5. The non-obvious mechanics

1. **Recursion guard** — every bot commit carries `[skip ci]`; GitHub skips push-triggered workflows for it
   (CI-11).
2. **…which bites the release branch** — it is cut from a tip that is usually a `[skip ci]` bot commit, so
   pushing it would skip the Release Build too. Trigger Release rewords the tip (`git commit --amend -m
   "chore(release): release X.Y.Z"`) whenever it carries a skip directive. Consequence: the release commit
   never exists on the default branch (CI-11).
3. **…which bites release notes** — `gh release create --generate-notes` picks the previous release among
   **ancestor** tags; none is an ancestor, so every release would list all history. `release.yml` computes the
   previous `v*` tag explicitly (`git ls-remote --tags` + `sort -V`, works on a shallow checkout) and passes
   `--notes-start-tag`; no previous tag → GitHub's default range (CI-12).
4. **Race-safe PATCH bump** — the workflow group serializes Publish runs, *and* the bump is a 5-attempt loop
   that fetches, `git switch -C <default> origin/<default>`, recomputes from the file, commits and pushes;
   on rejection it sleeps 3 s and retries. Recomputing from the latest tip keeps `versionCode` monotonic and
   makes `version.properties` conflicts impossible. Trigger Release does not retry (manual; re-run it)
   (CI-06, CI-07).
5. **Bump before build** — the APK carries the new version; a failed build after the push spends a version
   number with no artifact. Accepted.
6. **Release idempotency** — `gh release view` exists → `gh release upload --clobber` (notes untouched); else
   create with `--target <release branch>`. Re-pushing to `release/X.Y.Z` (a hotfix) replaces the asset.
   Trigger Release refuses a version whose branch already exists (CI-07, CI-13).
7. **Self-describing assets** — the APK is copied to `<slug>-<version>.apk` before upload.

## 6. Changing the pipelines

- Keep the four-workflow shape; add jobs (lint, detekt, size checks) to `pr-build.yml` rather than new
  triggers.
- A new required secret: add it to the runbook's table, make the workflow degrade (skip + `::notice::`)
  when it is absent, and update this file in the same PR.
- Pin actions to a major version (`@v4`); bump them deliberately.
- Changing the default branch name: update the three `branches:` filters and `DEFAULT_BRANCH` envs.
- Moving to store upload (Play / Firebase): add a step to `release.yml` after the GitHub Release; keep the
  run artifact.

## 7. Troubleshooting

| Symptom | Cause |
|---|---|
| Publish push rejected 5× | App not on the ruleset bypass list, or lacks Contents: write |
| `create-github-app-token` fails | App installed on the wrong account (must own the repo) |
| Release Build did not start | mode `github-app`: release tip still carries `[skip ci]`; mode `none`: missing `actions: write` |
| Release notes list everything | explicit `--notes-start-tag` removed |
| Signing step fails | only some of the four signing secrets are set |
| `VERSION_NAME must be MAJOR.MINOR.PATCH` | `version.properties` hand-edited into another shape |
