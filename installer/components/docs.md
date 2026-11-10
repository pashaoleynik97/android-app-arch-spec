# Component: docs (Core)

Rules: `specs/90-ai-workflow.md` (AI-29: docs change with the code; SKILL-16: long "why" stories become ADRs).
Shorthands as in `INSTALL.md`: `SPEC=.arch-spec/spec`, `R` = `python3 $SPEC/installer/render.py --target .`

## What it adds

| File | Purpose |
|---|---|
| `README.md` | project README skeleton: what it is (to fill), run / build, modules (pointer to `CLAUDE.md`), architecture pointers (skills, `.docs/`), testing, CI/CD (placeholder section), working with AI agents, deferred setup |
| `.docs/README.md` | the documentation model (skills = rules, `.docs/` = shipped behaviour, ADRs = decisions) and the layered topic-page template: Part 1 everyone (PO) · Part 2 QA (timings, edge-case matrix, reproduction) · Part 3 developers (boundaries, invariants, tests) · appendix |
| `.docs/decisions/ADR-0000-template.md` | the decision-record template |

An empty project has no README, so it is simply written. A README the user already has is never overwritten:
the new one arrives as `README.md.arch-spec-new` (rule 6). It also adds its line to the `components` block of `CLAUDE.md`.

## Prerequisites

- `foundation` installed. Install `docs` **last**, so the README's pointers match what is installed.

## Questions (all skippable)

| Parameter | Ask | Safe placeholder |
|---|---|---|
| `APP_NAME` | asked in round A already | derived |

Nothing else is asked. The README's description and CI/CD sections are left as `<…>` prose placeholders for the
user (they are not `{{…}}` parameters, so the placeholder gate does not flag them).

## Install

```
R plan docs
R install docs
```

Manual steps:
1. Fill the two `<…>` sections of `README.md` (description, CI/CD) **only from what the user actually said**.
   Never invent a product description; an unfilled section stays as a marked TODO and is listed in the report; with `ci-cd` installed, the CI/CD section points at `.github/workflows/`, `ci_runbook.md` and
   `docs/qa-installing-builds.md`.
2. If `README.md.arch-spec-new` appeared, merge the user's content into the new skeleton with them, then delete
   the `.arch-spec-new` file.

## Gate

None of its own. Run the final checks of `INSTALL.md` Step 3 (`no-placeholders-left.sh`, skill lint, check-refs,
build + tests, `test-summary.sh`).

## Completing deferred items

None. Topic pages in `.docs/` are written when a mechanism that crosses screens or modules ships (session,
caching, offline behaviour …), following `.docs/README.md`.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `README.md.arch-spec-new` appears | the existing README was not the stock one — merge by hand, delete the `.arch-spec-new` file |
| README names a component that is not installed | the CI/CD and device-testing sentences are conditional; edit them to match |

## Commit message

`chore(arch-spec): docs [spec vX.Y.Z]`
