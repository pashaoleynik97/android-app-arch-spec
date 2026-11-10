# New skill — when an area outgrows its skill

Create a skill when an area has enough rules that loading it with an unrelated skill wastes context, or when
it needs its own trigger (e.g. an on-demand procedure). Prefer a new reference in an existing skill when the
area is still small.

## Steps

1. **Name it**: lowercase letters, digits and single hyphens, ≤ 64 chars, a noun for the area
   (`offline-sync`, `release-process`).
2. **Create** the skill folder under `.claude/skills/` with:
   - `SKILL.md` — frontmatter (`name` = folder; `description` whose first sentence says what it covers and
     when to use it; `ON-DEMAND ONLY.` first if it must never run unasked; add
     `disable-model-invocation: true` only for a skill with side effects that must run only when the user
     asks), then: purpose (2–3 lines) → the rules most tasks need → a references index with an empty marker
     block → "Changing this skill" pointing here;

     ```markdown
     ## References

     <!-- <arch-spec:references> -->
     <!-- </arch-spec:references> -->
     ```
   - `CHANGELOG.md` with a first entry `## <YYYY-MM-DD> — new skill`.
3. **Move, don't copy**, the rules it takes over from other skills: cut them from the old skill, keep their
   IDs and origin markers unchanged, leave a one-line pointer in the old skill, and log the move in both
   changelogs.
4. **Make it discoverable**: add one bullet to "Architecture & conventions" in `CLAUDE.md` naming the skill as
   the source of truth for its area and when to load it (flag on-demand skills as such).
5. **Lint**: `bash tools/skills/lint.sh && bash tools/skills/check-refs.sh`.
