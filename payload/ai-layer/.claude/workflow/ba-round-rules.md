# Question round — file structure

A round collects questions for a business analyst, product owner or any other non-developer decision maker.
File: `NN-ba-questions.md` in the task folder, continuing the task's numbering. Template:
`.claude/workflow/templates/ba-round.md`.

## File

```markdown
# <TASK-ID> — questions, round <N>

## Q1 — <topic, a few words>

**Question.** <what the decision maker must decide>

**Sources.**
- <document · section>: «<verbatim quote>»
- <document · section>: «<verbatim quote>»

**Safe default.** <what we build if it is accepted> — <why it is safe>
```

- Order: the questions that change the most of what gets built go first.
- Nothing else in the file: no intro, summary, status or closing text.
- Question ids stay the same across rounds; a new question takes the next free number.

## Fields

| Field | Required | Omit when |
|---|---|---|
| Question | always | — |
| Sources | if texts conflict or stop short | nothing has been written on the topic |
| Safe default | if a safe option exists | every option risks data, money or a wrong business outcome |

Omit the whole field. Never write "N/A" or "none".

### Question
- One decision per question. Split anything that needs two answers.
- Understandable without opening the code or the analysis; no internal class names.
- Name the concrete case: "a 90-minute booking that crosses midnight", not "edge-case durations".
- If the possible answers are known, list them inside the question ("A or B?").

### Sources
- Quote verbatim, in the original language, only the fragment that matters; mark cuts with `…`.
- The locator must be enough to find the text: document + section, endpoint + field, ticket + comment, design
  frame link.
- Contradiction: at least two quotes that visibly disagree.
- Gap: quote the closest text, then add `Not covered: <what is missing>`.
- A design frame or the current app behaviour has no text to quote: state in one line what it shows.

### Safe default
- One concrete behaviour, as the user would see it.
- Safe means no data loss, no irreversible action, and a small change if the answer turns out different.
- It is a proposal to confirm, not a decision already taken.

## Does not belong in a round
- Anything the documents, the API description, the design or an earlier answer already settles.
- Implementation choices the reader has no stake in.
- Questions without a consequence: if every answer leads to the same build, drop the question.
- Background, restated requirements, praise, hedging, impact essays.

## Lifecycle
1. The analysis splits open questions into "decided by the developer now" and "escalated" (into a round).
2. **Safe defaults are built immediately**, so work does not wait for answers; unanswered ones are flagged in
   the pull request.
3. Answers are saved in the task folder (e.g. `answers-round-<N>.md`).
4. A refinement step re-analyses with the answers (and any updated sources), plans the difference
   (`NN-refinements-round-<N>.md`) and verifies it.

## Example

```markdown
# <TASK-ID> — questions, round 1

## Q1 — Archived projects in search: shown or hidden?

**Question.** Should the search screen include archived projects — yes, hidden, or only behind a filter?

**Sources.**
- Design spec · Search: «results show every project the user can open»
- API description · `GET /projects?query=`: «archived projects are excluded unless `includeArchived=true`»

**Safe default.** Hide archived projects (the API default) and add no filter yet — nothing is lost, and showing
them later is a one-parameter change.

## Q2 — Empty state wording

**Question.** What should the empty search state say, and does it offer "create a project"?

**Safe default.** Show "No projects match" with no action — display only, no stored data is affected.
```
