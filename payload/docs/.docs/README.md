# .docs — how the shipped app behaves

`.docs/` explains **behaviour that is shipped**, for the people who build, test and decide on the app. It is not
the rulebook — coding rules live in `.claude/skills/` — and it is never aspirational: a page describes what the
code does today and is updated in the same change as the code.

## Where documentation lives

| Location | Holds | Audience |
|---|---|---|
| `.claude/skills/` | rules and conventions, with reasons | developers, AI agents |
| `.docs/<topic>.md` | how a feature or mechanism behaves end to end | product, QA, developers |
| `.docs/decisions/ADR-NNNN-<slug>.md` | why a significant decision was taken, alternatives rejected | developers |
| `README.md` | what the project is, how to build and run it | everyone |
| `CLAUDE.md` | thin index for agents | AI agents |

## A topic page — layered by audience

Write one page per mechanism that crosses screens or modules (session lifecycle, caching and refresh, offline
behaviour, a multi-step flow). Each page has three parts, so every reader can stop when they have what they
need:

```markdown
# <Topic>

Part 1 is for everyone, Part 2 for QA, Part 3 for developers. Nothing here is aspirational: it describes the
shipped behaviour and is updated with the code.

## Part 1 — for everyone
- The N things that must be true (a short table).
- The behaviour in plain language; a glossary of the terms used.

## Part 2 — for QA
- Every timing constant in one table (interval, timeout, TTL — value and where it is defined).
- What the user sees in each state; what happens on each interaction and each failure.
- "Deliberately invisible — do not file as bugs".
- Numbered edge-case matrix: | # | Situation | Expected result |
- How to reproduce each case (e.g. a scripted server response per case number, when the mitm-testing
  component is installed).

## Part 3 — for developers
- Module boundaries and the pieces involved; start-up sequence; lifecycles.
- One failure travelling through the chain, step by step.
- Policies and invariants that must not be broken; how to extend it correctly.
- Testing: | Area | Test class that pins it |

## Appendix
- Wire contracts touched; error code → behaviour table; known gaps and deferred work.
```

The QA edge-case matrix doubles as the checklist for device verification, and every row should be traceable
to a test class or a reproduction script.

## Decisions

Record a decision as an ADR when it is surprising, costly to reverse, or rejects an obvious alternative (for
example enabling R8, adding product flavors, choosing a persistence library). Copy
`decisions/ADR-0000-template.md` to the next free number. A skill rule that rests on an ADR keeps a one-line
reason and links the ADR.

## Keeping it in sync

A task plan lists ".docs affected? yes/no"; a change that alters a described behaviour updates the page in the
same change.
