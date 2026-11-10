# Component: ai-layer (Core)

Rules: `specs/90-ai-workflow.md` (AI, SKILL). Shorthands as in `INSTALL.md`: `SPEC=.arch-spec/spec`,
`R` = `python3 $SPEC/installer/render.py --target .`

## What it adds

| File | Purpose |
|---|---|
| `CLAUDE.md` | thin index (≤ 150 lines): project line, skill index, `modules` and `components` marker blocks, build/verify commands, non-negotiables, task-workflow and deferred-items pointers |
| `.claude/settings.json` | committed allow-list: `./gradlew …`, read-only git, `tools/skills/*`, `render.py status` / `plan`, the spec gates, `adb devices`; denies force-push and reading keystores / `keystore.properties`. No hook enabled. |
| `.claude/.gitignore` | keeps `settings.local.json` (personal permissions) out of git |
| `.claude/rules/{gradle,compose-ui,tests,domain}.md` | path-scoped reminders (`paths:` frontmatter) pointing at the owning skill |
| `.claude/skills/application-architecture/` | `SKILL.md` (golden rules + empty `references` marker block; components add their lines) + `CHANGELOG.md` |
| `.claude/skills/compose-ui/` | same shape for the design system |
| `.claude/skills/add-feature/` | wraps `R scaffold-feature`, host wiring, first screen, capture |
| `.claude/skills/skills-maintenance/` | the six procedures (amend, capture, audit, hygiene, new-skill, upgrade) in `references/` |
| `.claude/workflow/general.md`, `ba-round-rules.md`, `templates/*.md` | the committed task workflow (task, analysis, questions, plan, implementation log, verification, question round) |
| `tools/skills/lint.sh`, `check-refs.sh`, `skills_tool.py`, `check-refs.ignore`, `remind-capture.sh` | skill tooling (standard-library Python via `python3 -I`; bash 3.2 compatible) and the optional `Stop` hook script |

Marker blocks in the files it ships: `CLAUDE.md` `modules` + `components`; each `SKILL.md` `references`. Every
installed component's insert lines are re-applied on install, so a component installed **before** ai-layer
(foundation) gets its lines now, and later components add theirs.

## Prerequisites

- `foundation` installed (the tooling resolves modules from `settings.gradle.kts`).
- `python3` on the PATH (lint and check-refs). `architecture` is **not** required: the ai-layer files name only
  things that exist after foundation, and the architecture references arrive with that component.

## Questions (all skippable)

| Parameter / question | Ask | Safe default |
|---|---|---|
| `APP_NAME`, `PROJECT_SLUG`, `ROOT_PACKAGE`, `CONVENTION_PREFIX`, `THEME_PREFIX` | asked in round A already | derived values |
| **reminder hook** (not a parameter) | "Enable a Claude Code Stop hook that reminds the agent to update skills when Gradle files, build-logic, a new module or the UI kit changed without a skill change?" | **no** (off) |

## Install

```
R plan ai-layer
R install ai-layer
```

Manual steps (the renderer never touches `.git/`):

1. **Exclude the task scratch folder locally** (AI-20 — `.git/info/exclude`, not `.gitignore`, so the exclusion
   itself is never committed):
   ```
   mkdir -p .git/info
   grep -qxF '.claude/no-commit-files/*' .git/info/exclude 2>/dev/null || echo '.claude/no-commit-files/*' >> .git/info/exclude
   ```
   Tell the user each collaborator does the same once per clone (the line is in `CLAUDE.md`, "Working on a task").
2. **Reminder hook — only if the user said yes**: add to `.claude/settings.json` (top level, next to
   `permissions`):
   ```json
   "hooks": {
     "Stop": [ { "hooks": [ { "type": "command", "command": "bash tools/skills/remind-capture.sh" } ] } ]
   }
   ```
   The script exits silently unless convention-bearing files changed without a `.claude/skills/` or `.docs/`
   change, and honours `stop_hook_active`, so it fires at most once per stop. Validate the JSON afterwards
   (`python3 -m json.tool .claude/settings.json`).
3. If the user has personal permissions, they go into `.claude/settings.local.json` (git-ignored), never into
   the committed file.

### The baseline snapshot

Every file `render.py` writes is also copied, untouched, to `.arch-spec/baseline/<path>` (marker-block edits
are not re-copied). It is the *base* of the three-way comparison the `skills-maintenance` **upgrade**
procedure performs (base = baseline, ours = working tree, theirs = the newer spec rendered over a copy of the
baseline). `.arch-spec/` is committed; the baseline is never edited by hand — only the upgrade procedure
replaces it, so declined upstream changes are not offered again.

## Gate

```
bash tools/skills/lint.sh
bash tools/skills/check-refs.sh
```
Both exit 0 on a fresh install (warnings allowed, errors not). They also run in the doctor mode and, with
`ci-cd`, on every pull request.

## Completing deferred items

ai-layer has no deferrable parameters. The hook can be enabled or removed at any time (step 2); the
`skills-maintenance` skill documents the same snippet.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `lint: python3 is required` | install Python 3; no packages are needed |
| `ERROR … not linked from SKILL.md` | a component's reference arrived without its index line: re-run `R install <that component>` (inserts are re-applied) or add the line inside the skill's `references` marker block |
| `ERROR … path ... does not exist` right after install | a skill names a file of a component that is not installed — report it as a spec bug; locally, reword the line or append `<!-- check-refs: ignore -->` |
| `ERROR … module ... is not included` | the module was renamed or removed; update the skill (skills-maintenance, amend) |
| `WARN … type ... is not declared` | a renamed class (fix the skill) or an external library type (add it to `tools/skills/check-refs.ignore`) |
| `marker block <arch-spec:references> not found` during a later install | the marker lines were deleted from a `SKILL.md`; restore the two comment lines from `.arch-spec/baseline/` |
| `.arch-spec-new` files for skills or `CLAUDE.md` | the project edited them; merge per rule as in the upgrade procedure, then delete the `.arch-spec-new` file |

## Commit message

`chore(arch-spec): ai-layer [spec vX.Y.Z]`
