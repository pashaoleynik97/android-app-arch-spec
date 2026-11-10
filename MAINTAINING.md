# Maintaining this spec

## Principles

- **The spec is the product.** `specs/` states the rules; `payload/` is the reference implementation of those
  rules; `installer/` turns the payload into a project. A change to one of them usually needs the other two.
- **A rule without a reason gets deleted by the next agent.** Every rule carries a *why*.
- **Versions are advice.** They live only in `payload/foundation/gradle/libs.versions.toml` and
  `specs/toolchain.md`. A gotcha in prose is phrased in terms of versions ("on AGP ≥ 9 …"), never as "currently".

## Making a change

1. Edit the spec (`specs/…`), keeping rule IDs stable: never renumber or reuse one. A retired rule stays as
   `~~ID~~ (removed in vX.Y)`.
2. Update the payload code and its tests, the matching target-skill reference
   (`payload/<component>/.claude/skills/…/references/*.md`) and, if needed, `installer/components/<id>.md`.
3. New placeholder? Add it to `installer/parameters.md` **and** to `DEFAULTS` (plus `VALIDATORS` / `DEFERRABLE` if
   applicable) in `installer/render.py`. Its safe value must compile.
4. New component? Add it to `installer/components.yml` with a doc, a payload dir, gates, marker inserts (skill
   index line, CLAUDE.md component line) and `specs/recommended/<id>.md`. It must build on top of its declared
   dependencies alone.
5. Run the self-checks:
   ```
   python3 installer/render.py --self-check
   bash installer/gates/domain-leak.sh .
   bash installer/gates/skills-lint.sh .
   ```
6. Run the dry runs (below). Then add a `CHANGELOG.md` entry that says what changes in an installed project and
   whether the upgrade is automatic. Build the entry from `git diff --stat <last tag> -- payload specs installer`,
   not from memory. Every changed rule ID, every renamed or moved rendered path, every new gate, every new
   placeholder and every agent-configuration change (`.claude/settings.json`) gets a line. An upgrading project
   relies on this list to know what to look at.

## Dry runs (the only real proof)

Each scenario starts from a fresh copy of `tests/fixtures/empty-activity/`:

| Scenario | Components | Must pass |
|---|---|---|
| startup | Core only, every question skipped | `assembleDebug testDebugUnitTest`, all gates |
| backend app | Core + network, auth-session, connectivity, paging, ci-cd, mitm-testing; then *add* `images` | same + component gates |
| everything | Core + every Recommended component, non-default answers | same + `assembleRelease` |
| evolve | on "backend app": amend a rule, rename a module and audit, upgrade from a bumped spec with a project override | override survives; audit flags stale refs |

```
T=$(mktemp -d)/proj && cp -R tests/fixtures/empty-activity "$T" && (cd "$T" && git init -q)
mkdir -p "$T/.arch-spec" && rsync -a --exclude .git --exclude .idea --exclude tests/fixtures ./ "$T/.arch-spec/spec/"
python3 "$T/.arch-spec/spec/installer/render.py" init --target "$T"
# … install components as INSTALL.md prescribes, then in $T:
./gradlew assembleDebug testDebugUnitTest
```

The installer itself is an agent procedure. At least once per minor release, run the scenarios through a **fresh
agent that sees only this repo**. Give it the instruction "follow `.arch-spec/spec/INSTALL.md`" and nothing else.
Wherever it hesitates, guesses or asks a question the spec should have answered, fix the spec.

## Toolchain updates

1. Bump versions in the catalog and the table in `specs/toolchain.md` together.
2. Run the "everything" dry run.
3. Every gotcha in `specs/toolchain.md` must still be true, or be rewritten with its new version bound. Add any new
   gotcha you hit, with its symptom (the exact error text helps future agents find it).

## Releasing

1. Bump `VERSION` and `.claude-plugin/plugin.json` `version` together. The plugin keeps users on the pinned version
   until it changes.
2. `CHANGELOG.md` entry.
3. Tag `vX.Y.Z`.
