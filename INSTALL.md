# INSTALL — rolling the architecture into an Android project

You are an AI coding agent working inside an Android Studio project. The user has placed this spec at
`.arch-spec/spec/` in the project root and asked you to follow this file. Read it top to bottom before acting.

Throughout, `$SPEC` means `.arch-spec/spec`, and **`R`** is the renderer. Define it once per shell as a
**function**: a string variable breaks in zsh, which doesn't word-split.

```bash
R() { python3 .arch-spec/spec/installer/render.py --target . "$@"; }
```

Run every command from the project root. Multi-line shell snippets assume `bash`; in zsh, run them as
`bash -c '…'`, or define helpers as functions as above. Paths like `$SPEC/specs/…` and `$SPEC/installer/…` always point inside
the spec copy. Never edit files under `.arch-spec/spec/`.

---

## 1. Golden rules

1. **Ask, never guess. Accept "skip".** Every question in the questionnaire may be answered with *skip* or *don't
   know yet*. Use the safe placeholder, record the item as deferred, and continue. Never block an install on a
   missing answer, and never invent a real-looking value such as a URL, colour or package.
2. **The renderer writes files; you don't.** Payload files are laid down only through `render.py`. You still edit
   code by hand where a component doc says so, and you resolve conflicts. You never re-type payload content
   yourself.
3. **Gates before commits.** After each component, run its `gate` commands from `installer/components.yml` and show
   the command plus the tail of its output. A red gate stops the install. Fix the cause or report it; never skip a
   gate silently.
4. **One commit per component** on branch `arch-spec/install`, message `chore(arch-spec): <component> [spec vX.Y.Z]`,
   where `X.Y.Z` is the content of `$SPEC/VERSION`. Never push, never merge (the user merges), never commit secrets.
5. **Nothing external.** Do not create GitHub Apps, secrets, rulesets or remote repos, and do not publish anything.
   List those steps for the user instead.
6. **The user's work wins.** A file that differs from what the spec last rendered is the user's. `render.py` writes
   the new version beside it as `<file>.arch-spec-new` (exit code 2). Show the diff, ask, merge, then delete the
   `.arch-spec-new` file.
7. **Core is rules, Recommended is advice.** Core components (`foundation`, `ai-layer`, `architecture`, `docs`) are
   installed as-is. Recommended components are offered with their *When to use / When not to* from
   `specs/recommended/<id>.md`, and installed only on an explicit yes.

## 2. Pick the mode

Check whether `.arch-spec/state.json` exists, and what the user asked for:

| Situation | Mode | Go to |
|---|---|---|
| no state file | **fresh** install | §3 |
| "install / add `<component>`" | **add** | §4 |
| "the design / backend / signing is ready", or a deferred item is named | **complete deferred** | §5 |
| "add a feature `<name>`" | **add feature** | §6 |
| a newer spec was copied into `.arch-spec/spec/` (`render.py status` shows *available* > *installed*) | **upgrade** | §7 |
| "status", "what is installed", "check the project" | **doctor** | §8 |

---

## 3. Fresh install

### Step 0 — preflight

Steps 0–1 change nothing. Run the checks read-only and report them in one short table. Only after the user's
go-ahead do you create the branch and run `R init`.
- **Empty project.** Expected: `settings.gradle.kts`, an `:app` module with a stock `MainActivity` and `ui/theme`.
  - If more than the stock project exists, stop and ask. Installing over real code means only **add** mode, one
    component at a time.
- **Git.**
  - The working tree must be clean **except for the untracked `.arch-spec/`** you were given. If there is no repo
    yet, run `git init` with the user's OK.
  - On go-ahead, create branch `arch-spec/install` and commit the spec copy first:
    `git add .arch-spec && git commit -m "chore(arch-spec): add spec vX.Y.Z"`. It stays committed, because later
    sessions, CI and upgrades need it.
- **Toolchain.**
  - Required: JDK 17 or newer (`java -version`). Newer is fine, because the Gradle daemon JVM is pinned separately
    by `gradle/gradle-daemon-jvm.properties`.
  - Required: an Android SDK with the `compileSdk` platform from `$SPEC/specs/toolchain.md`. Look for
    `$ANDROID_HOME/platforms/android-<N>*`, which may be named `android-<N>.0`. If it is missing, tell the user to
    install it from Android Studio → SDK Manager (or `sdkmanager "platforms;android-<N>"` when cmdline-tools
    exist), or offer the newest installed platform and record the deviation.
  - Optional: `python3` and `mitmdump` (mitm-testing), `gh` (ci-cd), `actionlint` or `ruby` (workflow check),
    `adb` + an AVD (verification). Missing optional tools only weaken those components' gates; say which.
- Run `R init` after the go-ahead. It derives the app name, application id, minSdk and project slug from the stock
  project, records the stock namespace, and prints the resolved values.

### Step 1 — questionnaire

Follow `$SPEC/installer/questionnaire.md`:
- Ask **at most three short rounds** and show defaults inline.
- Round B also gathers the *Questions* section of every selected component's doc, so nothing is asked later.
- Record the answers with `R set KEY=VALUE …`. A skipped item is `KEY=-`.
- Show the resolved summary, including what will be deferred, and get a go-ahead.

### Step 2 — install components in order

```
foundation → ai-layer → architecture → <selected recommended, dependencies first> → docs
```

For each component `C`:
1. Read `$SPEC/installer/components/C.md`, the *install procedure*. The *why* is in `$SPEC/specs/…`; read it only
   when the procedure points there.
2. `R plan C` (dry run). Show the user anything unusual, such as replaced stock files or conflicts. The plan
   doesn't list marker lines for files that don't exist yet; those are applied when their owner is installed.
3. `R install C`. Exit code 2 means conflicts; resolve them as in rule 6. Never use `--fresh` in a normal install.
   It re-applies `replaces` and `removes` as if the component were new, and is only for repairing a broken
   install, on the user's request.
4. Do the manual steps from `C.md`, if any. A step that edits `.claude/settings.json` or other agent
   configuration is **never done by you**: show the user the exact entries and let them add them.
5. Run C's gates. If one is red, diagnose using the troubleshooting section and `$SPEC/specs/toolchain.md`. If it
   stays red, stop and report.
6. Commit.

### Step 3 — final checks and report

```
bash $SPEC/installer/gates/no-placeholders-left.sh .
bash tools/skills/lint.sh && bash tools/skills/check-refs.sh
./gradlew assembleDebug testDebugUnitTest
bash $SPEC/installer/gates/test-summary.sh .       # test counts per module, from the JUnit XML results
R status
```

Report to the user with:
- **Installed** components, with gate evidence: the command, its result, and test counts from `test-summary.sh`.
- **Deferred** items exactly as `R status` lists them: the item and its `how` line. Items of components that
  aren't installed are not listed; they are not deferred, they are absent.
- **Manual steps for you**: CI secrets, GitHub App, keystore, SDK packages, bundling a font.
- **Next steps**:
  - "read `CLAUDE.md`";
  - "add your first feature: ask me to *add feature <name>*";
  - "when the design or backend arrives: ask me to *complete deferred design* / *network*".

---

## 4. Add a component later

1. `R status` shows what is installed. Read `$SPEC/specs/recommended/<id>.md` and tell the user when the component
   fits and when it doesn't.
2. Ask only this component's *Questions* (`$SPEC/installer/components/<id>.md`), all skippable. Record them with
   `R set`.
3. If a dependency is missing, offer to install it first. It goes through the same steps.
4. Work on a new branch `arch-spec/add-<id>` from the current branch. Run Step 2 for the component, then the Step 3
   checks, and report in the Step 3 format. The component adds its own skill reference files and index lines, so
   the skills describe the new capability automatically.

## 5. Complete deferred items

`R status` lists `deferred` with hints. For each item the user can now answer:

1. `R set KEY=VALUE`
2. `R rerender KEY [KEY …]`. This re-renders only the files that use those placeholders and that nobody has edited
   since. Edited files get `.arch-spec-new` siblings; resolve them as in rule 6.
3. Do any extra step named by the component doc, e.g. dropping font files into `lib-android/src/main/res/font/` for
   `design.typography`, or adding the keystore for `ci.signing`.
4. Run the gates of the affected components, then commit
   `chore(arch-spec): complete <item>`.

Design arriving later (palette, typography, Figma) is the most common case. Follow `installer/components/architecture.md` §
"Completing the design".

## 6. Add a feature

Use the installed `add-feature` skill. It wraps `R scaffold-feature <name>`, then:
- wires any host seams;
- writes the feature's first real screen with the user;
- updates the skills through the `skills-maintenance` **capture** procedure.

## 7. Upgrade to a newer spec

The user replaces `.arch-spec/spec/` with the newer copy (delete the old folder first, so removed files don't
linger). Then follow the installed `skills-maintenance` skill, procedure **upgrade**
(`.claude/skills/skills-maintenance/references/upgrade.md`). It is the single upgrade procedure, for code files
as well as skills:
- `R snapshot` produces *theirs*;
- merge base / ours / theirs;
- `R rebase` finishes.

A module the project renamed is handled by `R relocate` (procedure **rename-module**).

## 8. Doctor (read-only)

Run `R status`, `tools/skills/lint.sh` and `tools/skills/check-refs.sh`, then spot-check the Core rules in
`$SPEC/specs/00-constitution.md` against the code. Report:
- installed / deferred / modified-since-install;
- unresolved `.arch-spec-new` files;
- skill lint results;
- rule violations, each with its rule ID and file:line.

Change nothing.
