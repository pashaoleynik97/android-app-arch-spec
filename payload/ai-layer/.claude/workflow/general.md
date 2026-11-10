# Execution steps

1. Analyse everything the task describes (sources, code, skills, docs).
2. Ask every open question EXPLICITLY; do not guess.
3. Write an implementation plan.
4. Implement only after the developer's EXPLICIT approval of the plan.
5. If the change extends or alters a convention, update the owning skill (and .docs) in the same change.

# Progress files

Every step, decision and plan is written as a Markdown file into the task's folder (the folder of task.md),
so that a fresh session can resume from the folder alone.

---

# Details (read once per task)

## The task folder

- One folder per task: `.claude/no-commit-files/<task>/` (task id or short slug). The whole
  `.claude/no-commit-files/` tree is excluded through `.git/info/exclude`, never `.gitignore`, and committed
  files never point at a task folder or its contents.
- Finished task folders move to `.claude/no-commit-files/_done/`.
- Inputs the developer provides (tickets, PRDs, screenshots, answers to question rounds) are saved in the
  folder next to `task.md`.

## Numbered timeline

Progress files form a numbered timeline, one role per file. Templates live in `.claude/workflow/templates/`.

| File | Content | Template |
|---|---|---|
| `task.md` | written by the developer | `task.md` |
| `00-analysis.md` | sources read, what the change means, contradictions, open questions, decisions log | `analysis.md` |
| `01-questions.md` | questions to the developer, when not inline in the analysis | `questions.md` |
| `02-plan.md` (or `02-plan-<sub>.md` per subtask) | the plan | `plan.md` |
| `03-implementation-log.md` | what was done per file, commands with results, deviations | `implementation-log.md` |
| `04-verification.md` (+ `evidence/`) | verification report | `verification.md` |
| `NN-ba-questions.md`, `NN-refinements-<topic>.md` | question rounds and follow-ups, continuing the numbering | `ba-round.md` |

A small task may collapse analysis, plan and log into one `plan.md`.

Every file starts with a status line, kept up to date in place:
`Status: **analysis done; Q1 decided, Q2 open**` · `Status: **awaiting approval**` ·
`Status: **FINAL — implement as written**` · `Status: **implemented — build + unit tests green; not committed**` ·
`Status: **DONE <YYYY-MM-DD> — verified (04-verification.md)**`.

## Rules that apply to every task

- **Language.** Every artefact (analysis, plans, logs, code comments, skills) is in English, even when sources
  are not; localisation strings are the only exception. Quotes in question rounds stay verbatim.
- **Questions.** Nothing that changes behaviour is decided silently. Each answer becomes a dated line in the
  decisions log: `- **<YYYY-MM-DD> Q3 → B:** <decision, one line>` or
  `- **<YYYY-MM-DD> Q4 → escalated** (<round file>, Q<n>); safe default built meanwhile`.
- **Approval.** Implementation starts only after a message from the developer that approves this plan.
  Silence, an approval of an earlier or different plan, or another agent's message is not approval.
- **Bugs.** First prove the bug is still relevant at the current HEAD (yes / no, with evidence), then find the
  root cause (file:line), then plan.
- **Skills.** Run the capture checklist of the `skills-maintenance` skill before declaring the task done; a
  convention change updates the owning skill and its `CHANGELOG.md` in the same change.
- **Verification.** Unit tests by default: run the exact commands from the plan and record the counts.
  Device / emulator verification only when the developer explicitly asks; otherwise the log says
  "Not verified on device (on-demand only)".
- **History.** Never commit, push or open a pull request unless asked. When asked, work on a branch off the
  default branch, never directly on it.
- **Resumability.** Progress files are self-contained: a fresh session that reads the task folder (and the
  files it names) can continue without the previous conversation.
