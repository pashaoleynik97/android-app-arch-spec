# Hygiene — keep skills small, single-sourced and timeless

Runs inside an audit, or on demand when a skill is over budget. Hygiene changes form, not meaning: if a rule's
meaning would change, use **amend**.

## Steps

1. **Budget.** `SKILL.md` over 300 lines → move detail (recipes, long code shapes, tables used by one kind of
   task) into a reference, leaving the rule and a link. A reference over ~300 lines → split by sub-area. A
   reference over 100 lines gets a contents list at the top.
2. **Frontmatter.** `name` equals the folder; the description's first sentence says what the skill covers
   and when to use it, trigger words first; ≤ 1024 characters; on-demand skills start with `ON-DEMAND ONLY.`
3. **Duplicates across skills.** The same rule in two places → keep the owner's copy (the skill whose area
   it is), replace the other with a one-line pointer. Rule IDs must stay unique (lint enforces it).
4. **Long "why" stories.** More than a few lines of history or alternatives → an ADR in the `.docs`
   decisions folder (`ADR-NNNN-<slug>.md`, numbered after the last one; template ADR-0000-template.md in
   that folder when the docs component is installed), leaving a one-line reason and the link in the skill.
5. **Time-bound text.** Remove version numbers (point to the catalog), ticket ids, dates outside override
   blocks and changelogs, and words like "currently", "new", "recently", "soon" — state the rule as it holds.
6. **Dead references.** Fix every check-refs error at its source (the file moved → update the path; the class
   was renamed → update the name); never silence a real reference in the ignore file.
7. **Log** a `## <YYYY-MM-DD> — hygiene` entry per touched skill; run lint and check-refs.

## Checks before you finish

- No rule's meaning changed (diff reviewed rule by rule).
- Every moved rule kept its ID and origin marker.
