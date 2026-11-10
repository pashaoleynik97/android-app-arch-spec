# Upgrade — take changes from a newer architecture spec

Use when a newer spec copy was placed in `.arch-spec/spec/` (`render.py status` shows
`spec_version_available` above `spec_version_installed`). The project keeps every deliberate change; it takes
upstream changes only by choice.

Contents: 1. Inputs · 2. Produce "theirs" · 3. Merge skills, rules files and CLAUDE.md per rule ·
4. Every other file · 5. Finish

## 1. Inputs

- `.arch-spec/state.json`: installed spec version, answers, components in `install_order`, features, and
  `relocations` (modules the project renamed). Read it through `render.py --target . status`.
- **base**: `.arch-spec/baseline/`, the exact text the old spec produced, marker lines included. Never edited by
  hand.
- **ours**: the project's working tree.
- **theirs**: what the new spec produces for the same answers, components, features and relocations (step 2).
- The new spec's `CHANGELOG.md`, from the installed version up to the new one. Read it first and summarise for
  the developer what changes for this project.

## 2. Produce "theirs"

```bash
R() { python3 .arch-spec/spec/installer/render.py --target . "$@"; }
THEIRS=$(mktemp -d)/theirs
R snapshot --out "$THEIRS"
```

`snapshot` renders every installed component in install order, the features and all marker lines into an
empty directory, applying the recorded relocations. For a project that changed nothing, it is byte-identical to
the working tree. Compare three ways with plain tools (`diff -ru .arch-spec/baseline "$THEIRS"` to see upstream
changes, `diff -u <base> <ours>` per file).

## 3. Skills, rules files and CLAUDE.md — merge per rule, never per file

Split each file into units: a **rule** runs from a rule line (`**ID** — …`, struck or not) to the line before
the next rule line or heading, including its origin marker and any override block; **non-rule prose** is
merged per heading section. Match units by rule ID (prose by heading). Then:

| base → theirs | base → ours | Action |
|---|---|---|
| same | same | nothing |
| changed | same | **offer adoption**: show the diff; apply on approval |
| same | changed | keep ours |
| changed | changed | **conflict**: show base / ours / theirs side by side; default keep ours |
| any | ours has an override block | the override is never replaced. Show the upstream diff of the overridden rule and ask whether to **fold** any of it into the override's `Rule:` (a separate amend, with its own changelog line) |
| any | origin `project` | never touched |
| added | — | offer to add it, with its origin marker, where theirs places it |
| removed / struck | same | offer removal |
| removed / struck | changed or overridden | keep ours; report |

Marker blocks (`arch-spec:references`, `arch-spec:modules`, `arch-spec:components`) are merged by line: keep
every line of ours, and add the lines theirs adds. A line that names a module the project renamed is already
mapped by `snapshot`; never re-add an old name.

## 4. Every other file

Code, Gradle files and docs follow the installer's file-level rule:
- ours == base: take theirs.
- ours != base: show base / ours / theirs, merge with the developer, keep ours by default.
- A file theirs no longer renders is reported, never deleted silently.

Two exceptions:
- **Agent configuration** (`.claude/settings.json`, hooks, anything that grants permissions) is never changed
  by you, even when ours == base. Hand the exact change to the developer.
- **Relocated modules**: `snapshot` already maps their paths. Package renames inside them, if the project made
  any, show up as ours != base and are kept.

## 5. Finish

1. Run `R rebase --snapshot "$THEIRS"`. The snapshot becomes the new baseline, checksums are re-recorded from
   the project's current files, files the new spec stopped rendering leave the bookkeeping, and the installed
   version moves up. Run it only after every conflict is resolved; it refuses while `.arch-spec-new` files
   exist.
2. Add a `CHANGELOG.md` entry to every touched skill:
   ```markdown
   ## <YYYY-MM-DD> — upgrade <old> → <new>
   - adopted: <IDs>
   - declined: <IDs> (<why>)
   - conflicts kept as ours: <IDs>
   - upstream changed overridden rules: <IDs> (folded in: <IDs> | not folded: <IDs>)
   - handed to the developer: <agent-config changes>
   ```
3. Run `bash tools/skills/lint.sh`, `bash tools/skills/check-refs.sh`, and every gate listed by `R status` →
   `gates`. Those gates are already mapped through relocations.
4. Commit as `chore(arch-spec): upgrade to v<new>` on a branch, only when the developer asks.
