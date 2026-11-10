---
name: skills-maintenance
description: >
  Procedures for changing the project's skills — amend a rule, capture a new convention after a task, audit
  skills against the code, tidy them, create a skill, or upgrade from a newer architecture spec. Use whenever a
  convention changes, a task ends with a new pattern, or skills may be stale.
---

# Skills maintenance

The skills in `.claude/skills/` are this project's living rules. After install they belong to the project:
the spec never rewrites them silently, and they change only through the procedures below, each in its own
reference. `CLAUDE.md` and `.claude/rules/` only point at skills; when they disagree, the skill wins and the
pointer is fixed in the same change.

## Procedures

| Procedure | When | Reference |
|---|---|---|
| amend | "we now do X instead of Y", or a code change alters an existing convention | [amend.md](references/amend.md) |
| capture | end of any task that introduced a pattern or a decision | [capture.md](references/capture.md) |
| audit | periodically, before a release, or when asked to check the skills | [audit.md](references/audit.md) |
| hygiene | inside audit, or when a skill outgrows its budget | [hygiene.md](references/hygiene.md) |
| new skill | an area outgrows its current skill | [new-skill.md](references/new-skill.md) |
| upgrade | a newer spec copy was placed in `.arch-spec/spec/` | [upgrade.md](references/upgrade.md) |
| rename module | a module the installer created is renamed | [rename-module.md](references/rename-module.md) |

Load only the reference for the procedure at hand.

## The format every skill follows

- **Frontmatter**: `name` equals the folder (lowercase, digits, single hyphens, ≤ 64 chars); `description`
  1–1024 chars, third person, first sentence = what it covers and when to use it; an on-demand skill starts
  with `ON-DEMAND ONLY.` Portable keys only (`name`, `description`, `license`, `compatibility`, `metadata`,
  `allowed-tools`) unless a Claude-specific key is needed.
- **Budget**: `SKILL.md` ≤ 300 lines (hard cap 500); detail in the skill's `references` folder, one level deep, each
  ≤ ~300 lines, a contents list when over 100 lines; every reference linked from `SKILL.md`.
- **Structure of `SKILL.md`**: purpose → the rules that apply to most tasks → references index inside the
  `arch-spec:references` marker block (one line each: link — what it covers — `(component: <id>)` or
  `(project)`) → "Changing this skill".
- **A rule** is one paragraph, then its origin marker on the next line:

```markdown
**PRJ-DATA-01** — <MUST / SHOULD statement>. *Why:* <reason>.
<!-- origin: project -->
```

  Spec rules keep their spec ID and carry `<!-- origin: arch-spec <ID>[, <ID>…] -->`. Project rules get
  `PRJ-<AREA>-<NN>` IDs, unique across all skills, never renumbered or reused. A removed rule stays as
  `~~<ID>~~ — removed <YYYY-MM-DD>: <reason>`.
- **Overriding a spec rule**: strike it through, keep its origin marker, add an override block directly
  under it (an override is a first-class rule, not a violation):

```markdown
~~**DATA-51** — … original statement …~~
<!-- origin: arch-spec DATA-51 -->
> **Project override** — DATA-51 — 2026-03-14
> Rule: <the rule as this project applies it>
> Reason: <why the project differs>
```

- **History**: each skill has a `CHANGELOG.md` next to `SKILL.md`, never linked from it (it must not load
  uninvited). Entries newest first: `## <YYYY-MM-DD> — <procedure>`, then `- <ID>: <what changed> (<why>)`.
- **Never in a skill**: version numbers (point to `gradle/libs.versions.toml`), ticket ids, dates outside
  override blocks and the changelog, time-relative words ("currently", "new", "recently", "soon"), secrets,
  machine-specific absolute paths, links into the git-excluded task folders.
- **Scripts** a skill needs live in its own `scripts` folder; the skill says whether to run or read each.

## Tooling

| Command | Checks |
|---|---|
| `bash tools/skills/lint.sh` | frontmatter, budgets, reference links, rule IDs unique, origin markers, override blocks, versions, `CLAUDE.md` size, `paths:` in rules files |
| `bash tools/skills/check-refs.sh` | every inline backticked path, module, Gradle task and type in skills, rules files and `CLAUDE.md` still exists |

Both print `ERROR|WARN <path>:<line>: <message>`, exit 1 on errors, and accept `--strict` (warnings fail
too). Fenced code blocks are not checked by check-refs; a line ending in `<!-- check-refs: ignore -->` is
skipped; genuinely external names go in `tools/skills/check-refs.ignore`. Both run in the doctor mode of
`.arch-spec/spec/INSTALL.md`, and in the pull-request workflow when the ci-cd component is installed.

## Reminder hook (optional)

A Claude Code Stop hook can nudge the agent to run **capture** when Gradle files, `build-logic/`, a new
module or the UI kit changed but no skill or `.docs` page did. It is off unless the developer enabled it at
install time. To enable it, add to `.claude/settings.json`:

```json
"hooks": {
  "Stop": [
    { "hooks": [ { "type": "command", "command": "bash tools/skills/remind-capture.sh" } ] }
  ]
}
```

`tools/skills/remind-capture.sh` fires at most once per stop (it honours `stop_hook_active`) and prints
nothing when no convention-bearing file changed. Remove the block to disable it.

## The installer's baseline

`.arch-spec/baseline/` holds the pristine copy of every file the installer rendered — the *base* of the
three-way comparison the **upgrade** procedure performs. It is committed and never edited by hand; only the
upgrade procedure replaces it.

## References

<!-- <arch-spec:references> -->
- [amend.md](references/amend.md) — change an existing rule safely (component: ai-layer)
- [capture.md](references/capture.md) — turn a task's new pattern or decision into a rule (component: ai-layer)
- [audit.md](references/audit.md) — find drift between skills and code, with evidence (component: ai-layer)
- [hygiene.md](references/hygiene.md) — keep skills within budget, deduplicated and timeless (component: ai-layer)
- [rename-module.md](references/rename-module.md) — rename an installer-created module and teach the renderer (component: ai-layer)
- [new-skill.md](references/new-skill.md) — create a skill and move rules into it (component: ai-layer)
- [upgrade.md](references/upgrade.md) — take changes from a newer spec, rule by rule (component: ai-layer)
<!-- </arch-spec:references> -->

## Changing this skill

This skill follows its own procedures: amend or capture, then a `CHANGELOG.md` entry next to this file.
