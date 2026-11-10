# Capture — turn a task's new pattern or decision into a rule

Run before declaring any task done (the task workflow requires it). Most tasks need no skill change; the
checklist makes that a decision instead of an omission.

## Checklist

1. **Is anything here reusable beyond this task?** A new seam type, a new kit component or token, a new
   screen or flow shape, a new Gradle convention or toolchain gotcha, a test technique, a naming choice, a
   decision that rejected an obvious alternative. If nothing → write "no skill change needed because: …" in
   the task's implementation log and stop.
2. **Which skill owns it?** Exactly one owner. Modules, build, DI, kernel, ViewModels, domain, tests →
   **application-architecture**; tokens, kit, chrome, layout → **compose-ui**; skill tooling → this skill.
   No good owner and the area is large → **new skill**.
3. **What kind of knowledge is it?**
   - a rule that applies to most tasks in the area → the skill's `SKILL.md`;
   - detail, a recipe, a code shape → the area's reference file (create one if no reference fits, and link it
     from the references index with `(project)`);
   - a long "why" story (alternatives, trade-offs, history) → an ADR in the `.docs` decisions folder, with a
     one-line reason and a link left in the skill.
4. **Does it contradict an existing rule?** Yes → stop and run **amend** instead.
5. **Write it**:
   ```markdown
   **PRJ-<AREA>-<NN>** — <MUST / SHOULD statement>. *Why:* <reason>.
   <!-- origin: project -->
   ```
   Pick the next free `NN` for that area across **all** skills
   (`grep -rhoE "PRJ-<AREA>-[0-9]+" .claude/skills | sort -u`). Keep it short; a code shape of a few lines
   may follow.
6. **Log it** in that skill's `CHANGELOG.md`: `## <YYYY-MM-DD> — capture` / `- PRJ-<AREA>-<NN>: added (<why>)`.
7. **Lint**: `bash tools/skills/lint.sh && bash tools/skills/check-refs.sh`.
8. If the behaviour is described for QA or product in a `.docs` page, update that page too.

## Good captures

- State the rule, not the story of the task ("a picker screen keeps its query in the route", not "in this
  task we found that…").
- Name real files, modules and types only — check-refs verifies them.
- No ticket ids, dates, versions or time-relative words.
