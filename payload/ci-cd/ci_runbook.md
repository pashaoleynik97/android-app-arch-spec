# CI/CD — one-time setup runbook

Everything the four GitHub Actions workflows need, in the order to do it. All of it is self-serve with
repository admin rights. Nothing here is required for a **green** pipeline: with no secrets and bot mode
`none`, every workflow still runs and builds (debug-key signing, no version bump).

| Workflow | Trigger | Does |
|---|---|---|
| `pr-build.yml` | pull request into `{{DEFAULT_BRANCH}}` | `testDebugUnitTest` + `:app:assembleDebug` (+ skills lint if present) |
| `publish.yml` | push to `{{DEFAULT_BRANCH}}` (a merge) | tests → bump PATCH (bot mode `github-app`) → release + debug APKs `-test` → run artifact |
| `release-trigger.yml` | manual, checkboxes major / minor / patch | semver bump with reset → cut `release/<version>` |
| `release.yml` | push to `release/**` (or dispatch) | tests → release APK (no suffix) → run artifact + GitHub Release `v<version>` |

## 0. Decide the bot mode

The version-bump commit has to land on a protected branch. Two modes, selected by the repository
**variable** `CI_BOT_PUSH` (Settings → Secrets and variables → Actions → *Variables*); without the variable
the value chosen at install time (`{{CI_BOT_PUSH}}`) applies.

| Mode | Version on every merge | Needs |
|---|---|---|
| `github-app` | PATCH auto-bumped and pushed back (`chore(release): bump version to X.Y.Z [skip ci]`) | a GitHub App + 2 secrets + a ruleset bypass (steps 1, 3) |
| `none` | `versionName` = `version.properties` (bump it by PR); `versionCode` = commit count | nothing |

Why an App and not the workflow token or a personal token: the workflow token cannot bypass a ruleset,
and its pushes never trigger other workflows (so the release branch would not start the Release Build).
An App token is short-lived, minted per run, not tied to a person, can be put on the bypass list, and its
pushes do trigger workflows. `[skip ci]` in every bot commit stops the recursion.

Switching later: `none` → `github-app` is safe (versionCode jumps up to the semver formula). The reverse
lowers versionCode — uninstall old builds on QA devices first.

## 1. Create the GitHub App (mode `github-app` only)

Create it **under the account that owns the repository**. If the repository belongs to an organization,
create the App in the organization: an App installed on a personal account gets no access to org repos.

1. Owner → Settings → Developer settings → GitHub Apps → **New GitHub App**.
   - Name: e.g. `{{PROJECT_SLUG}}-release-bot`. Homepage URL: the repository URL.
   - **Uncheck** Webhook → Active.
   - Permissions → Repository → **Contents: Read and write**. Nothing else.
   - Create.
2. Note the **App ID** → secret `APP_ID`.
3. **Generate a private key** (downloads a `.pem`) → paste the whole file, BEGIN/END lines included, into
   secret `APP_PRIVATE_KEY`.
4. **Install App** → the owning account → **Only select repositories** → this repository.
5. Set the repository variable `CI_BOT_PUSH=github-app` (unless it was rendered that way).

## 2. Release signing (optional, both modes)

Without these secrets release APKs are signed with the debug key — fine for internal QA, never for users.

1. Create or locate the release keystore, kept at the repo root as `{{KEYSTORE_FILE}}` (gitignored — never
   commit it), and fill a local `keystore.properties` from `keystore.properties.template` so IDE builds
   sign with the same key.
2. Encode it on one line and check the round-trip:
   ```bash
   base64 -i {{KEYSTORE_FILE}} | tr -d '\n' > /tmp/ks.b64
   base64 -d < /tmp/ks.b64 | cmp - {{KEYSTORE_FILE}} && echo OK
   ```
   (On Linux use `base64 -w0 {{KEYSTORE_FILE}}`.) Paste the content into secret `KEYSTORE_BASE64`, then delete
   `/tmp/ks.b64`.
3. Secrets `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` ← `keystorePassword`, `keyAlias`, `keyPassword`
   from `keystore.properties`.

With `gh`: `gh secret set KEYSTORE_BASE64 < /tmp/ks.b64`, `gh secret set KEY_ALIAS`, … (prompts for the value).

### Secrets and variables

| Name | Kind | Used by | Content |
|---|---|---|---|
| `CI_BOT_PUSH` | variable | 2, 3, 4 | `github-app` or `none` (overrides the rendered default) |
| `APP_ID` | secret | 2, 3 | the App's numeric id |
| `APP_PRIVATE_KEY` | secret | 2, 3 | the App's `.pem` |
| `KEYSTORE_BASE64` | secret | 2, 4 | the keystore, base64 on one line |
| `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` | secret | 2, 4 | from `keystore.properties` |

The Release Build uses the built-in workflow token (`contents: write`) for `gh release`.

## 3. Branch ruleset

Settings → Rules → Rulesets → **New branch ruleset**:
- Name `{{DEFAULT_BRANCH}}-protection`, enforcement **Active**, target the default branch.
- Rules: **Require a pull request before merging**; **Require status checks to pass** → `Unit tests + debug build`.
- Mode `github-app`: **Bypass list → Add bypass → GitHub Apps → the release bot.** Humans still go through
  pull requests; only the App pushes the bump directly.
- Optional second ruleset for `release/**`: in mode `github-app` add the App to its bypass list too (it
  pushes the release branch); in mode `none` such a ruleset blocks the trigger — leave `release/**` open.

## 4. Verify end to end

1. Open a throwaway PR → **PR Build** is green.
2. Merge it → **Publish** runs. Mode `github-app`: a bump commit `1.0.0 → 1.0.1` appears and does **not**
   start another run; artifact `{{PROJECT_SLUG}}-1.0.1-test`. Mode `none`: artifact
   `{{PROJECT_SLUG}}-1.0.0-build<N>-test`, no commit.
3. Actions → **Trigger Release** → Run workflow → tick `minor` → `release/1.1.0` appears (mode `github-app`
   also pushes `1.1.0` to the default branch).
4. **Release Build** runs on that branch → artifact `{{PROJECT_SLUG}}-release-1.1.0` and Release `v1.1.0`
   with `{{PROJECT_SLUG}}-1.1.0.apk` attached. Its notes start at the previous `v*` tag.

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| Publish: `push rejected` five times | the App is not on the ruleset bypass list, or lacks Contents: write |
| `create-github-app-token` fails: not installed | App installed on the wrong account (step 1) |
| Release Build never starts after Trigger Release | mode `github-app`: the release branch tip still says `[skip ci]` (the reword step was removed?); mode `none`: the dispatch step lacks `actions: write` |
| Every release lists the whole history | the `--notes-start-tag` computation was dropped from `release.yml` |
| Build fails at signing with an empty password | only some signing secrets are set — set all four or none |
| `VERSION_NAME must be MAJOR.MINOR.PATCH` | `version.properties` was hand-edited into another shape |
