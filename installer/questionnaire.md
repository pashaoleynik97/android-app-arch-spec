# Questionnaire

Asked once, in Step 1 of a fresh install. The rules:
- **Every answer may be "skip".** Skipped answers use the safe placeholder from `parameters.md`, and deferrable ones
  are recorded for later.
- Show the derived default next to each question, e.g. "App name [My App]".
- Ask at most **three rounds**. A user with an idea and nothing else answers round A, says "skip" to B and C, and
  still gets a building project.
- Record each round right away: `render.py set --target . KEY=VALUE …`, and `KEY=-` for a skip.
- Validation errors from `set` are re-asked with the rule shown. Never "fix" an answer silently.

## Round A — identity (needed by everything; defaults derived from the empty project)

1. App name → `APP_NAME`. It must not contain `' " & < > \ $`.
2. Project slug → `PROJECT_SLUG`. Default: the app name in kebab-case (`Field Tasks` → `field-tasks`). It is used
   for `rootProject.name`, file names and CI artifact names.
3. Application id → `APPLICATION_ID`. Warn that it can't change after the first store release.
4. Kotlin namespace of `:app` → `APP_PACKAGE`. Default: the application id. The stock files under the empty
   project's original namespace are removed by the install either way.
5. Package root for modules → `ROOT_PACKAGE`. Default: the namespace minus its last segment
   (`com.acme.shop` → `com.acme`).
6. Short prefix for build conventions → `CONVENTION_PREFIX`. Lowercase letters and digits only. Default: the last
   segment of the package root (`acme` → `acme.android.library`).
7. Design-system prefix → `THEME_PREFIX`. PascalCase. Default: the app name in PascalCase (`Acme` → `AcmeTheme`,
   `AcmeButton`).
8. Minimum SDK → `MIN_SDK`.

Every default above is derived from the answer before it. Show the whole chain resolved, so the user sees, for
example, `com.example` before accepting it.

## Round B — scope

9. Which **Recommended** components? For each one, give a one-line "use it when …" from
   `$SPEC/specs/recommended/<id>.md`.
   - Pre-tick **nothing**.
   - Suggest `network` + `connectivity` + `mitm-testing` when the user says the app talks to a backend.
   - Suggest `ci-cd` when the project is on GitHub.
   - The full list is in `components.yml` (tier `recommended`).
10. **Questions of the selected components.** Ask here the *Questions* section of each selected component's
    `$SPEC/installer/components/<id>.md`, so nothing is asked mid-install. That includes:
    - backend URLs `BASE_URL_DEBUG` / `BASE_URL_RELEASE` and health path `HEALTH_PATH`. All are deferrable; a skip
      means `https://api.example.invalid/`, so the app builds and runs while requests fail fast and nothing
      pretends to work;
    - for `ci-cd`: `DEFAULT_BRANCH`, `CI_BOT_PUSH` (`github-app` | `none`) and `KEYSTORE_FILE`;
    - for `images`: the files host and the credential style.
11. **Core questions:**
    - The optional AI "capture reminder" hook (ai-layer, default off).
    - Whether to fill the README description now. Only from what the user has actually said; otherwise it is left
      as a marked TODO.
12. **Toolchain:** the known-good set from `$SPEC/specs/toolchain.md` (default), or *latest* (upgrade after
    `foundation` and re-verify every gotcha).

## Round C — design (all deferrable; "we have no design yet" means skip the whole round)

13. Primary colour (`#RRGGBB`) → `COLOR_PRIMARY`, plus optional error/success/warning colours.
14. Font → `FONT_FAMILY` (`system`, or the family name of font files the user will add).
15. Smallest target screen in dp → `REFERENCE_DEVICE_DP`, e.g. `360x640`. Previews are sized to it.
16. Theme mode → `THEME_MODE` (`light-only` | `light-dark`).
17. Figma link → `FIGMA_URL`. Recorded so later agents know where the design lives.
18. Extra locales → `EXTRA_LOCALES`, e.g. `de,fr`. *None* is an answer (`EXTRA_LOCALES=`), not a skip.

## Summary shown before installing

```
Identity     My App · com.example.myapp · root com.example · conventions `example.*` · prefix `MyApp`
Core         foundation, ai-layer, architecture, docs
Recommended  network, connectivity, mitm-testing
Deferred     design.palette, design.typography, network.baseUrl   (you can complete these any time)
Toolchain    known-good set (answer 12)
Proceed? [yes / change …]
```

If the user asks for the **latest** toolchain instead of the known-good set, update the catalog after `foundation`
installs. Then re-check every gotcha in `specs/toolchain.md` and run the gates. Report any version that had to be
held back, with the reason.
