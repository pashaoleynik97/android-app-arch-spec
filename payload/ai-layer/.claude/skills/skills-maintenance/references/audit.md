# Audit — find drift between the skills and the code

Run periodically, before a release, or when asked to check the skills. The audit **reports**; changes happen
afterwards through amend / capture, decided by the developer.

## Steps

1. **Tooling.** Run `bash tools/skills/lint.sh` and `bash tools/skills/check-refs.sh`; every error and warning
   becomes a finding.
2. **Sample code against rules.** For each skill, pick the rules that have a mechanical signature and grep for
   violations. Typical signatures:

   | Rule kind | Signature to grep |
   |---|---|
   | forbidden import | `import android.util.Log` outside the Reporter; a mocking library in test sources |
   | required annotation | ViewModels without `@HiltViewModel`; injected types in modules without the hilt convention |
   | module dependency | `project(":…")` lines in a feature build file naming another feature |
   | file location | a Hilt module outside a `di` package; routes outside the contract module |
   | hardcoded values | `Color(0x`, `.sp`, literal `.dp` in UI packages outside the theme |
   | versions | a version string in a build file instead of `libs.*` |

   Record each hit as file:line.
3. **Look for undocumented conventions**: the same helper shape, naming pattern or wiring in three or more
   places that no skill describes.
4. **Check overrides and project rules**: every Project override still has a reason that holds; every
   `PRJ-` rule still matches the code.
5. **Write the report** into the task folder for this audit (named `skills-audit-<YYYY-MM-DD>`, see
   `.claude/workflow/general.md` for where task folders live), file report.md:

   ```markdown
   # Skills audit — <YYYY-MM-DD>
   | # | Finding | Rule / skill § | Evidence (file:line) | Proposal: update skill / fix code | Decision |
   |---|---|---|---|---|---|
   ```

   Leave *Decision* empty — the developer fills it.
6. **Follow up** each decided row with **amend** (skill changes), **capture** (new rules), or a code fix task.
   Hygiene findings can be fixed in the same pass (see hygiene).

## Notes

- Evidence, not impressions: every row cites file:line or a tool output line.
- An audit never edits a skill or the code by itself.
