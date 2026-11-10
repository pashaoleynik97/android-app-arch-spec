# Amend — change an existing rule

Use when the developer says "we now do X instead of Y", or when a code change alters a convention a skill
already states. A new convention that no rule covers yet is **capture**, not amend.

## Steps

1. **Find every place the topic appears** — the owning rule and every pointer to it:
   ```
   grep -rn "<keyword or rule ID>" .claude/skills/ .claude/rules/ CLAUDE.md .docs/
   ```
   Note each hit as file:line. The owner is the one skill whose rule states it; everything else is a pointer.
2. **Check the request against the other rules first.**
   - **One request, several rules.** When it changes more than one rule (e.g. a notice mechanism touching the
     ViewModel-contract rule and the notices rule), amend each one, and cross-reference them in each rule's
     text ("see UI-16 override").
   - **Conflicting request.** When the requested wording breaks another rule (e.g. "a `StateFlow` field on the
     UxState" vs "UxState is plain data"), don't pick silently. Propose the closest compliant wording **and**
     the literal one, and let the developer choose. The literal choice then amends the conflicting rule too.
3. **Propose the edit as a diff** with the affected rule IDs (owner + pointers), and the reason. Wait for the
   developer's explicit approval; do not edit before it.
4. **Apply.**
   - The rule's origin marker is `arch-spec …` (a spec rule) and its text must now differ → do **not** edit it.
     Strike the statement through, keep the origin marker, and add a Project override block directly under it
     (format in `SKILL.md`, "Overriding a spec rule"): today's date, the rule as the project applies it, the
     reason. The block is every `>` line after the header, so a long `Rule:` may wrap across as many lines as it
     needs.
   - The rule is already overridden → edit the override block's `Rule:` / `Reason:` lines and its date.
   - The origin marker is `project` → edit the rule in place; keep its ID.
   - Retiring a rule → strike it and append `— removed <YYYY-MM-DD>: <reason>`; never delete the ID.
   - Pointers (`CLAUDE.md`, `.claude/rules/`, other skills, `.docs`) → reword to match, or reduce them to a
     pointer if they restated the rule.
5. **Log it** in the owning skill's `CHANGELOG.md`:
   ```markdown
   ## <YYYY-MM-DD> — amend
   - <ID>: <what changed> (<why>)
   ```
   Entries go newest first, directly under the `<!-- <arch-spec:changelog-entries> -->` line when the changelog
   has one, otherwise right after the file's introduction paragraph.
6. **Lint**: `bash tools/skills/lint.sh && bash tools/skills/check-refs.sh` — both must pass.
7. **List the code that now violates the amended rule** (file:line), found with a grep for the rule's
   mechanical signature. Fix it only if the developer asks; otherwise record the list in the task folder.

## Checks before you finish

- The rule exists in exactly one skill; everything else points at it.
- No spec rule was edited in place.
- The change and the code that motivated it are in the same change set.
