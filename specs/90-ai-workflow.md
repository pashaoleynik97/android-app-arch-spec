# 90 — AI collaboration: repository layer, task workflow, skill evolution (AI-*, SKILL-*)

Tier: **Core** (MUST), installed by the `ai-layer` component. Three parts:

- **A. Repository layer** — `CLAUDE.md`, skills, path-scoped rules, permissions (AI-01…09, SKILL-01…09).
- **B. Task workflow** — how an agent and a developer carry a task from request to verified change (AI-20…39).
- **C. Skill evolution** — ownership after install, the six maintenance procedures, tooling (SKILL-10…39).

Part C is written to be implemented directly: the `skills-maintenance` skill, `tools/skills/lint.sh`,
`tools/skills/check-refs.sh` and the optional hook follow it literally.

Target-side paths used below:

```
CLAUDE.md                              thin index (AI-01)
AGENTS.md                              optional one-line bridge (AI-04)
.claude/settings.json                  committed permissions + optional hook (AI-06, SKILL-34)
.claude/settings.local.json            personal, git-ignored
.claude/rules/*.md                     path-scoped rules (AI-05)
.claude/skills/<name>/SKILL.md         skills (SKILL-01…)
.claude/skills/<name>/references/*.md  one level deep
.claude/skills/<name>/CHANGELOG.md     per-skill change log (SKILL-13)
.claude/workflow/general.md            standing task rules (AI-21)
.claude/workflow/ba-round-rules.md     question-round format (AI-33)
.claude/workflow/templates/*.md        task, analysis, plan, verification, ba-round templates
.claude/no-commit-files/<task>/        git-excluded task folders (AI-20)
.docs/decisions/ADR-NNNN-<slug>.md     long "why" stories (SKILL-16)
tools/skills/                          lint.sh, check-refs.sh, skills_tool.py, check-refs.ignore, remind-capture.sh
.arch-spec/                            spec copy, state.json, baseline/ (written by the installer)
```

---

# Part A — Repository layer

**AI-01** — `CLAUDE.md` is a **thin index of at most 150 lines**. It contains only what an agent cannot infer
from the code, in this order: one-paragraph project line; "Architecture & conventions" — one bullet per skill
naming it the source of truth for its area and **when** to load it (on-demand skills flagged as such); the
module table (`<!-- <arch-spec:modules> -->` marker block); the dependency direction ("never invert"); the
feature-trio rule; **build/verify commands** (exact); non-negotiable toolchain constraints; a short conventions
list; notes (documents to keep in sync, the scratch-folder convention). Multi-step procedures never live in
`CLAUDE.md`.
*Why:* `CLAUDE.md` is loaded into every session in full; each line is a permanent context cost. Procedures belong
in skills, which load on demand.

**AI-02** — **Skills are the source of truth.** `CLAUDE.md`, rules files and docs point at skills instead of
restating them. When a pointer and a skill disagree, the skill wins and the pointer is fixed in the same change.
*Why:* duplicated rules drift; one owner per rule keeps them correct.

**AI-03** — The installed skill set is: `application-architecture` (modules, build, DI, kernel, presentation,
data, testing, CI), `compose-ui` (design system, UI kit, host chrome), `add-feature` (scaffold a feature trio),
`skills-maintenance` (Part C); with the `mitm-testing` component also `mitmproxy-testing` and
`feature-verification`. Each installed component adds its reference files and one index line per reference to
the owning skill's `<!-- <arch-spec:references> -->` block.
*Why:* a skill per area of judgement; components extend skills instead of creating skill sprawl.

**AI-04** — `AGENTS.md` MAY be added for other coding agents; if present it contains a pointer to `CLAUDE.md` and
the build/verify commands only.
*Why:* Claude Code reads `CLAUDE.md`; other agents read `AGENTS.md`; two full copies drift.

**AI-05** — File-scoped guidance lives in `.claude/rules/*.md` with a `paths:` frontmatter list of globs, so it
loads only when matching files are touched. Each rules file is ≤ 60 lines, names the skill/reference that owns
the topic, and lists only the handful of rules most often broken for those files. The installed set:

| File | `paths:` | Points to |
|---|---|---|
| `gradle.md` | `**/*.gradle.kts`, `gradle/libs.versions.toml`, `build-logic/**` | build-system reference |
| `compose-ui.md` | `**/src/main/**/ui/**/*.kt` | `compose-ui` skill |
| `tests.md` | `**/src/test/**/*.kt` | testing reference |
| `domain.md` | `**/domain/**/*.kt` | data-and-errors reference |

*Why:* the right reminder at the right moment, at zero cost elsewhere.

**AI-06** — A committed `.claude/settings.json` holds a **curated allow-list** for safe, frequent commands, grouped
by purpose: build (`./gradlew …`), read-only git (`git status`, `git diff`, `git log`, `git show`), skill tooling
(`bash tools/skills/…`), installer status (`python3 -I .arch-spec/spec/installer/render.py status …`), and — with
`mitm-testing` — `adb shell`, `adb exec-out`, `mitmdump`. It denies force-pushes and reading signing secrets
(`*.jks`, `keystore.properties`). Personal additions go to `.claude/settings.local.json`, which is git-ignored.
Commits and pushes are never pre-allowed.
*Why:* fewer approval prompts for routine work, without granting anything irreversible.

**AI-07** — All AI-workflow artefacts (task files, analyses, plans, logs, skills, rules) are written in English,
even when sources are in another language; localisation strings are the only exception. Quotes in question rounds
stay verbatim in their original language (AI-33).
*Why:* one working language keeps artefacts searchable and reviewable by everyone.

## Skill authoring

**SKILL-01** — A skill is `.claude/skills/<name>/SKILL.md` with YAML frontmatter. `name` equals the folder name
(lowercase letters, digits, single hyphens, ≤ 64 chars). `description` is 1–1024 characters, third person, and
its **first sentence states what the skill covers and when to use it** (trigger words first — listings truncate
long descriptions). An on-demand skill's description starts with `ON-DEMAND ONLY.` Knowledge skills use only the
portable keys (`name`, `description`, `license`, `compatibility`, `metadata`, `allowed-tools`); Claude-specific
keys (`disable-model-invocation`, `user-invocable`, `argument-hint`, `paths`) are used only where needed, e.g.
`disable-model-invocation: true` for a skill with side effects that must run only when the user asks.
*Why:* invalid or oversized frontmatter fails silently (the skill loads without its trigger); portable keys keep
skills usable by other agents.

**SKILL-02** — Size budgets: `SKILL.md` ≤ 300 lines (hard cap 500); detail in `references/*.md`, **one level
deep** (no subfolders, no reference that requires reading another reference); each reference ≤ ~300 lines; a
reference over 100 lines starts with a contents list.
*Why:* a loaded skill body stays in context for the session; progressive disclosure loads detail only when the
task needs it, and agents often read only the head of long files.

**SKILL-03** — `SKILL.md` structure: purpose (2–3 lines) → the rules that apply to most tasks in the area →
"References" index inside `<!-- <arch-spec:references> -->` … `<!-- </arch-spec:references> -->` (one line per
file: link — what it covers — `(component: <id>)` or `(project)`) → "Changing this skill" pointer to
`skills-maintenance`.
*Why:* the agent sees the always-relevant rules immediately and knows where everything else is.

**SKILL-04** — A rule is written as `**<ID>** — <MUST/SHOULD statement>. *Why:* <reason>.` followed by its origin
marker (SKILL-10). Short code shapes are allowed; long examples go to a reference.
*Why:* IDs make rules traceable to the spec and addressable by tooling; a rule without a reason gets "fixed" by
the next agent.

**SKILL-05** — Skills contain no version numbers (point to the version catalog), no ticket ids, no dates outside
override blocks and `CHANGELOG.md`, no time-relative words ("currently", "new", "recently", "soon"), no secrets, no
machine-specific absolute paths, and no references into `.claude/no-commit-files/`.
*Why:* all of these rot or leak; a skill states the rule as it holds now.

**SKILL-06** — Executable helpers live in `scripts/` inside the skill and the skill says whether to **run** or
**read** each one. Scripts handle their own errors and print actionable messages.
*Why:* fragile sequences (Gradle wiring, emulator setup) are safer as code than as prose.

**SKILL-07** — After install, **skills belong to the project.** The spec never rewrites them silently; changes
from a newer spec arrive only through the upgrade procedure (SKILL-20) with the developer's approval.
*Why:* the skills are the project's living rules; the project, not the spec, decides what they say.

---

# Part B — Task workflow

**AI-20** — Each task gets a folder `.claude/no-commit-files/<task>/` (task id or short slug). The folder is
excluded via `.git/info/exclude` (`.claude/no-commit-files/*`), **not** `.gitignore`. Committed files may describe
this convention but never point at a specific task folder or its contents. Finished task folders move to
`.claude/no-commit-files/_done/`.
*Why:* the scratch space is personal working memory, never shipped; excluding it locally keeps even the exclusion
out of history.

**AI-21** — The standing rules are committed in `.claude/workflow/general.md`:

```markdown
# Execution steps
1. Analyse everything the task describes (sources, code, skills, docs).
2. Ask every open question EXPLICITLY; do not guess.
3. Write an implementation plan.
4. Implement only after the developer's EXPLICIT approval of the plan.
5. If the change extends or alters a convention, update the owning skill (and .docs) in the same change.

# Progress files
Every step, decision and plan is written as a Markdown file into the task's folder (the folder of task.md),
so that a fresh session can resume from the folder alone.
```
*Why:* the same contract for every task and every agent; resumability survives interrupted sessions.

**AI-22** — `task.md` is written by the developer. First line:
`IMPORTANT: follow the rules in .claude/workflow/general.md`. Sections: `# Role` · `# Language` (artefacts in
English) · `# Context` (why this task exists; for follow-ups, which files to read) · `# Sources` (optional: docs,
API description URL, design links, tickets as files in the folder) · `# Task` (numbered requirements; precise
visual specs for UI work) · `# Out of scope` (optional). A bug task pastes the report verbatim (preconditions,
steps, actual, expected, environment; screenshots saved in the folder). Template:
`.claude/workflow/templates/task.md`.
*Why:* a fixed shape makes scope and sources explicit before work starts.

**AI-23** — Progress files form a **numbered timeline** in the task folder, one role per file:

| File | Content |
|---|---|
| `00-analysis.md` | sources read, what the change means, contradictions, open questions, decisions log |
| `01-questions.md` | questions to the developer (if not inline in the analysis) |
| `02-plan.md` (or `02-plan-<sub>.md` per subtask) | the plan (AI-26) |
| `03-implementation-log.md` | what was done, per file; commands run with results; deviations |
| `04-verification.md` | verification report (AI-34), evidence in `evidence/` |
| `NN-ba-questions.md`, `NN-refinements-…md` | question rounds and follow-ups, continuing the numbering |

A small task MAY collapse analysis, plan and logs into one `plan.md`. Every file starts with a status line kept
up to date: `Status: **analysis done; Q1 decided, Q2 open**` · `**awaiting approval**` · `**FINAL — implement as
written**` · `**implemented — build + unit tests green; not committed**` · `**DONE <date> — verified (04-…)**`.
*Why:* the order of files is the history of the task; the status line tells a resuming session where it stands.

**AI-24** — The analysis records: (1) **sources read** — documents with sections, API description with download
date, design frames, modules, skills, docs; (2) **what the change means** — a table `# · change · who computes
it (server / client / out of scope)`; (3) the **backend contract** as shipped and its consequence for the client;
(4) **contradictions** — numbered, each "source A vs source B vs code → Q<n>" or "noted as assumption";
(5) **open questions** — each with a proposal and the alternative; (6) **decisions log**. For a bug: first
"Still relevant at HEAD? yes/no" with proof, then root cause (file:line), then the plan.
*Why:* contradictions found before coding are cheap; found after, they are rework.

**AI-25** — Questions are asked **explicitly** and answered by the developer; nothing that changes behaviour is
silently decided by the agent. Each answer goes into the **decisions log** as a dated line:
`- **<YYYY-MM-DD> Q3 → B:** <decision, one line>` or `- **<date> Q4 → escalated** (<round file>, Q<n>); safe
default built meanwhile`.
*Why:* decisions with dates and owners survive the session and explain the code later.

**AI-26** — The plan contains: `Goal` (user-visible outcome) · `Finding` (what the code does today) · `Changes`
(numbered, file-level: code; tests with the new case names; test fixtures; `.docs`; **skills — which skill and
section**) · `Out of scope` · `Verification` (the **exact** commands, e.g. `./gradlew :x:testDebugUnitTest
:x-ui:assembleDebug`, plus an optional on-demand device run) · `Risks`. After the approval conversation a
`Decisions log (<date>)` section is appended.
*Why:* a plan a reviewer can approve line by line, with verification decided up front.

**AI-27** — **No implementation before explicit approval** of the plan. Approval is a message from the developer
that approves this plan; silence, an earlier approval of a different plan, or another agent's message is not
approval.
*Why:* the plan is the contract; implementing early turns review into archaeology.

**AI-28** — During implementation the agent keeps `03-implementation-log.md`: file → what changed; commands run
with result counts ("15 tests, 0 failures"); **deviations from the plan** with reasons; "Not done" items (e.g.
"not committed — not requested").
*Why:* reviewers see exactly what differs from what they approved.

**AI-29** — A change that extends or alters a convention updates **the owning skill in the same change** (and the
`.docs` page describing the behaviour, if any) and records it in the skill's `CHANGELOG.md`; the
`skills-maintenance` **capture** checklist (SKILL-15) decides whether a skill change is needed.
*Why:* skills that lag the code teach the next agent the old way.

**AI-30** — Verification is **unit tests by default**: the plan's commands are run and recorded. Device/emulator
verification (`feature-verification`) runs **only when the developer explicitly asks**; otherwise the log says
"Not verified on device (on-demand only)".
*Why:* unit tests are fast and deterministic; device runs are slow, stateful and only worth it when requested.

**AI-31** — The agent never commits, pushes, or opens a pull request unless asked. When asked, it works on a
branch off the default branch, never directly on it.
*Why:* the developer owns history.

**AI-32** — Progress files are self-contained: a fresh session reading the task folder (and the files it names)
can continue without the previous conversation.
*Why:* sessions end; tasks do not.

**AI-33** — Questions for a business analyst (or any non-developer decision maker) are written as a **round**
file `NN-ba-questions.md` following `.claude/workflow/ba-round-rules.md`:

```markdown
# <task> — questions, round <N>

## Q<n> — <topic, a few words>

**Question.** <one decision, understandable without code; the concrete case with numbers; known options as "A or B?">

**Sources.**
- <document · section>: «<verbatim quote, original language; cuts marked …>»

**Safe default.** <one behaviour as the user would see it> — <why it is safe>
```

Rules: order by build impact (largest first); question ids stable across rounds, new questions take the next
free number; nothing else in the file (no intro, summary or status). **Sources** is present when texts conflict
(≥ 2 disagreeing quotes) or stop short (closest quote + `Not covered: …`); a design frame or current app
behaviour is described in one line. **Safe default** is present when a safe option exists — safe means no data
loss, no irreversible action, and a small change if the answer differs; it is a proposal, not a decision.
Omit a field rather than writing "N/A". **Does not belong:** anything already settled by documents or earlier
answers, implementation choices the reader has no stake in, questions whose every answer leads to the same build,
background, restated requirements, praise, hedging. Lifecycle: the analysis splits open questions into "decided
by developer now" and "escalated"; **safe defaults are built immediately**; answers land in the task folder; a
refinement step re-analyses and plans the difference.
*Why:* decision makers answer precise, sourced, single-decision questions quickly; safe defaults keep work
moving without betting on the answer.

**AI-34** — A verification report (`04-verification.md`) has:

```markdown
# <task> — verification (<date>)
Environment: <device/emulator, API level, screen>, build <variant> from <working tree | commit>; scenario files
used (new ones marked); setup path. Evidence in `evidence/`.

## Unit / build
<commands> → <counts>

## Results
| # | Case | Expected | Observed / evidence | Result |

## Findings
None | numbered, each labelled regression / UX / pre-existing / out of scope

## Not verified
<what could not be reached and why>

## Environment state left
<proxy, running processes, signed-in state, installed certificates> + the undo command
```

Every PASS row cites evidence (request-log timestamps, UI-dump values, screenshot file names), never "looks
right". Contrast pairs and absence-of-a-call evidence follow `specs/70-testing.md` TEST-40/41.
*Why:* a verification anyone can audit; the next person knows what state the device is in.

---

# Part C — Skill evolution

## Ownership model

**SKILL-10** — Every rule in an installed skill carries an **origin marker** on the line after its statement:
`<!-- origin: arch-spec <RULE-ID> -->` for a rule shipped by the spec (the spec rule ID it implements, e.g.
`DATA-13`; several IDs comma-separated), or `<!-- origin: project -->` for a rule the project added.
Grammar: `^<!-- origin: (arch-spec [A-Z]+-\d+(, [A-Z]+-\d+)*|project) -->$`.
*Why:* tooling and upgrades must tell spec rules from project rules without guessing.

**SKILL-11** — Rules added by the project get IDs `PRJ-<AREA>-<NN>` (`<AREA>` any uppercase word, e.g.
`PRJ-DATA-01`). IDs are unique across all skills, never renumbered or reused; a removed rule stays as
`~~<ID>~~ — removed <YYYY-MM-DD>: <reason>`.
*Why:* stable addresses for audits, overrides and change logs.

**SKILL-12** — A project may replace a spec rule with a **Project override** block placed directly under the
original rule (which stays, struck through):

```markdown
~~**DATA-51** — … original statement …~~
<!-- origin: arch-spec DATA-51 -->
> **Project override** — DATA-51 — 2026-03-14
> Rule: <the rule as this project applies it>
> Reason: <why the project differs>
```
Grammar of the first line: `^> \*\*Project override\*\* — ([A-Z]+-\d+) — (\d{4}-\d{2}-\d{2})$`, followed by
`> Rule:` and `> Reason:` lines. An override is a first-class rule, not a violation.
*Why:* deliberate divergence is recorded with its reason and date, and upgrades can see it and leave it alone.

**SKILL-13** — Each skill has `CHANGELOG.md` next to `SKILL.md` (not linked from it, so it never loads
uninvited). Entries, newest first: `## <YYYY-MM-DD> — <procedure>` then bullets `- <ID>: <what changed> (<why>)`.
Upgrades also log adopted / declined / conflicting spec changes here.
*Why:* the history of a rule is available on demand without costing context.

## The `skills-maintenance` skill

**SKILL-14** — The installed `skills-maintenance` skill indexes six procedures, one reference file each:

| Reference | Procedure | Trigger |
|---|---|---|
| `amend.md` | amend | "we now do X instead of Y", or a code change that alters a convention |
| `capture.md` | capture | end of any task that introduced a pattern or decision |
| `audit.md` | audit (drift) | periodically, before a release, or `/skills-audit` |
| `hygiene.md` | hygiene | inside audit, or on demand |
| `new-skill.md` | new skill | an area outgrows its current skill |
| `upgrade.md` | upgrade from spec | a newer spec copy is placed in `.arch-spec/spec/` |

Its description: "Procedures for changing the project's skills — amend a rule, capture a new convention after a
task, audit skills against the code, tidy them, create a skill, or upgrade from a newer architecture spec. Use
whenever a convention changes, a task ends with a new pattern, or skills may be stale."
*Why:* skills that are hard rules for the project must still change with it, through repeatable procedures.

**SKILL-15** — **amend** — (1) Find every place the topic appears: `grep -rn` over `.claude/skills/`,
`.claude/rules/`, `CLAUDE.md`, `.docs/`. (2) Propose the edit as a diff with the affected rule IDs; wait for
approval. (3) Apply: a spec-origin rule that now differs gets an override block (SKILL-12), never a silent edit; a
project rule is edited in place. (4) Add a `CHANGELOG.md` entry. (5) Run `lint.sh` and `check-refs.sh`. (6) List
the code that now violates the amended rule (file:line); fix it only if asked.
**capture** — a checklist run before a task is declared done: Is this reusable beyond this task? Which skill owns
it (one owner)? Is it a rule (SKILL.md), detail (a reference), or a decision story (`.docs/decisions/`)? Does it
contradict an existing rule (→ amend)? Then write it with a `PRJ-` ID and origin `project`, log it, lint.
*Why:* amend changes existing rules safely; capture stops new conventions from living only in code.

**SKILL-16** — **audit** — (1) Run `lint.sh` and `check-refs.sh`. (2) Sample code against rules: for each skill,
pick its rules with a mechanical signature (forbidden imports, required annotations, module dependencies, file
locations) and grep for violations. (3) Look for repeated conventions no skill documents (the same helper shape in
≥ 3 places). (4) Write a report table to `.claude/no-commit-files/skills-audit-<YYYY-MM-DD>/report.md`:
`| # | Finding | Rule / skill § | Evidence (file:line) | Proposal: update skill / fix code | Decision |`.
The developer fills *Decision*; follow-ups run as amend/capture.
**hygiene** — keep `SKILL.md` within budget by moving detail into references; enforce frontmatter and description
rules; remove duplicates across skills (keep the owner's copy, replace others with a pointer); move long "why"
stories into `.docs/decisions/ADR-NNNN-<slug>.md` leaving a one-line reason and a link; remove time-bound text
(SKILL-05).
*Why:* drift is detected by evidence, decided by the developer, and fixed by the same procedures as any change.

**SKILL-17** — **new skill** — create `.claude/skills/<name>/` with `SKILL.md` (frontmatter per SKILL-01,
structure per SKILL-03, empty references block), `CHANGELOG.md`, and add a bullet to `CLAUDE.md`'s
"Architecture & conventions" naming the new skill as source of truth for its area and when to load it. Move —
don't copy — the rules it takes over from other skills, keeping their IDs and origin markers.
*Why:* a new skill must be discoverable and must not duplicate rules.

## Upgrade from a newer spec

**SKILL-20** — Inputs:
- `.arch-spec/state.json`: installed spec version, answers, components in `install_order`, features, `relocations`.
- The **baseline** `.arch-spec/baseline/`: the exact text the installer produced for every file, marker lines
  included. Skills are under `.arch-spec/baseline/.claude/skills/`.
- The project's working tree (**ours**).
- The newer spec copied to `.arch-spec/spec/`.

**Theirs** is produced deterministically by `render.py snapshot --out DIR`. It renders every installed component
in install order, the features, all marker lines and the recorded relocations into an empty directory. For an
unmodified project, the snapshot equals the working tree byte for byte.
*Why:* a three-way comparison needs the exact text the old spec produced (base), not a reconstruction.

**SKILL-21** — Skills, rules files and `CLAUDE.md` are merged **per rule** (a rule = from a rule line to the next
rule line or heading; non-rule prose = per heading section), never per file:

| base → theirs | base → ours | Action |
|---|---|---|
| same | same | nothing |
| changed | same | **offer adoption** (show diff); apply on approval |
| same | changed | keep ours |
| changed | changed | **conflict**: show base / ours / theirs side by side; default keep ours |
| any | rule has an override block | the override is never replaced. Show the upstream diff and ask whether to fold any of it into the override's `Rule:` (a separate amend) |
| any | origin `project` | never touched |
| added | — | offer to add (with its origin marker) |
| removed / struck | same | offer removal |
| removed / struck | changed or overridden | keep ours; report |

Afterwards: `render.py rebase --snapshot DIR`. The snapshot becomes the baseline, so declined changes are not
re-offered; checksums are re-recorded from the current files and the installed version moves up. Then add a `CHANGELOG.md` entry per skill listing adopted, declined and conflicting rule IDs;
run `lint.sh`, `check-refs.sh` and the gates `render.py status` lists. Non-skill files follow the file-level rule:
take theirs only where ours == base. **Agent configuration** (`.claude/settings.json`, hooks) is never changed by
the agent; the change is handed to the developer.
*Why:* the project keeps every deliberate change; it takes upstream fixes only by choice.

## Tooling

**SKILL-30** — `tools/skills/lint.sh` (a thin bash wrapper over a standard-library-only
`python3 -I tools/skills/skills_tool.py lint`) checks, over `.claude/skills/*`, `.claude/rules/*` and `CLAUDE.md`:

| # | Check | Level |
|---|---|---|
| 1 | `SKILL.md` exists; frontmatter is the first block, parses as `key: value` YAML | error |
| 2 | `name` present, equals folder, matches `^[a-z0-9]+(-[a-z0-9]+)*$`, ≤ 64 chars | error |
| 3 | `description` present, 1–1024 chars | error |
| 4 | frontmatter keys outside the SKILL-01 sets | warning |
| 5 | `SKILL.md` > 300 lines | warning; > 500 error |
| 6 | `references/` has no subdirectories | error |
| 7 | every `references/*.md` is linked from `SKILL.md`; every such link resolves | error |
| 8 | reference > 300 lines | warning; > 500 error; > 100 lines without a contents list → warning |
| 9 | rule IDs (`**ID** —` at line start or after `- `) defined once across all skills | error |
| 10 | each rule has a valid origin marker (SKILL-10) | warning |
| 11 | override blocks match SKILL-12 grammar and name a rule present in the same file | error |
| 12 | version-like strings (`\d+\.\d+\.\d+`) or references to `no-commit-files` in skills | warning |
| 13 | `CLAUDE.md` > 150 lines | warning; > 200 error |
| 14 | each `.claude/rules/*.md` has a non-empty `paths:` list | error |

Output: one line per finding, `ERROR|WARN <path>:<line>: <message>`, then a summary. Exit 0 = no errors, 1 =
errors, 2 = usage. `--strict` turns warnings into errors.
*Why:* frontmatter and size problems fail silently at runtime; only a script catches them every time.

**SKILL-31** — `tools/skills/check-refs.sh` (`skills_tool.py check-refs`) resolves every **inline** backticked
token in skills, rules files and `CLAUDE.md` (fenced code blocks are skipped; a line containing
`<!-- check-refs: ignore -->` is skipped; tokens containing `<`, `>`, `{{`, `…` or `*` placeholders other than
globs are skipped as templates):

| Token kind | Recognised as | Resolves when | Level |
|---|---|---|---|
| path | contains `/` or ends in a known extension (`.kt .kts .md .toml .xml .json .yml .yaml .sh .py .properties`) | exists from the repo root (globs: ≥ 1 match) | error |
| module | `^:[a-z0-9-]+(:[a-z0-9-]+)*$` | included in `settings.gradle.kts` | error |
| task | `:module:task` or `./gradlew …` | the module part exists | error |
| type | PascalCase identifier | declared (`class/interface/object/enum class/annotation class/typealias`) in a `src/**/*.kt`, or listed in `tools/skills/check-refs.ignore` | warning |

`check-refs.ignore` holds one token or anchored regex per line (shipped with common Kotlin/AndroidX/JUnit names).
Output and exit codes as SKILL-30.
*Why:* a skill that names a moved file or a renamed module teaches the wrong thing with full confidence.

**SKILL-32** — The **baseline** and `state.json` are written only by `render.py` (install, rerender, relocate,
rebase), never by hand. A renamed module is recorded with `render.py relocate OLD NEW`, which keeps later installs,
gates and upgrades on the new name. `.arch-spec/` is committed.
*Why:* the baseline is what makes three-way upgrades possible for every collaborator.

**SKILL-33** — With the Recommended `ci-cd` component, the pull-request workflow runs
`bash tools/skills/lint.sh && bash tools/skills/check-refs.sh`, and the PR template carries the checkbox
`- [ ] Skills / .docs updated (or not needed because: …)`.
*Why:* a cheap, visible reminder at the point where drift enters.

**SKILL-34** — Optional reminder hook (off by default; the installer asks): `.claude/settings.json` registers a
`Stop` hook running `bash tools/skills/remind-capture.sh`. The script reads the hook JSON from stdin, exits 0 at
once if `stop_hook_active` is true, otherwise collects changed paths (`git diff --name-only HEAD` plus untracked
files). If any matches `**/*.gradle.kts`, `settings.gradle.kts`, `gradle/libs.versions.toml`, `build-logic/**`,
a new module directory, or the UI kit package, **and** nothing under `.claude/skills/` or `.docs/` changed, it
prints `{"decision":"block","reason":"Convention-bearing files changed (<list>). Run the skills-maintenance
capture checklist, or state why no skill change is needed."}`; otherwise prints nothing.
*Why:* a nudge exactly when a convention may have changed; the `stop_hook_active` guard makes it fire once.

**SKILL-35** — `INSTALL.md`'s doctor mode reports skill health: lint and check-refs results, number of overrides
and project rules, deferred installer items, and the installed vs available spec version.
*Why:* one place to see whether the AI layer is still trustworthy.

---

## Adapting this to your app

- Task folder naming, the `_done/` archive and template wording are project choices; the order analysis →
  explicit questions → plan → explicit approval → implement → verify is not.
- Teams with a different issue tracker keep the task-folder workflow; `task.md` simply links the ticket.
- Projects that do not use Claude Code keep Parts B and C; `AGENTS.md` (AI-04) points other agents at the same
  files, and the reminder hook (SKILL-34) is skipped.
