---
name: add-feature
description: >
  Adds a new feature to this app as a three-module trio (contract, UI, domain): scaffolds it with the
  architecture installer, wires it into the host (registration, navigation seams, sibling ports), writes its
  first real screen with the developer, and records any new convention in the skills. Use this whenever the
  developer asks to add, create or scaffold a feature, a new screen flow, or a new feature module.
---

# Add a feature

A feature `x` is three modules — `x-ui-api` (FeatureApi marker + routes), `x-ui` (FeatureImpl, screens,
ViewModels), `x` (domain) — rendered from the installer's feature template, the same shape as the reference
feature `home`. The installer writes the files; you wire, design and implement. Module rules:
**application-architecture** (feature-trio and presentation references). Screen rules: **compose-ui**.

Follow the task workflow (`.claude/workflow/general.md`): steps 3–6 below are planned and approved like any
other change.

## 1. Agree on the name and scope

- Ask for the feature id if it is not given: kebab-case, letters / digits / dashes, starting with a letter
  (`order-history`). It becomes the module names, the package segment (dashes dropped: `orderhistory`) and
  the class prefix (OrderHistory).
- Ask what the first screen shows and does, where it is entered from (start destination? a sibling's
  button?), and whether it needs a domain module at all (a trivial feature may omit it — delete the domain
  module after scaffolding and remove its include and dependency lines).
- Check the name is free: no module directory with that name exists.

## 2. Scaffold

Run (from the repository root):

```
python3 .arch-spec/spec/installer/render.py status --target .                         # architecture installed?
python3 .arch-spec/spec/installer/render.py scaffold-feature --target . <name> --dry-run
python3 .arch-spec/spec/installer/render.py scaffold-feature --target . <name>
./gradlew :<name>-ui:assembleDebug :<name>:testDebugUnitTest :<name>-ui:testDebugUnitTest
```

The scaffold writes the three modules and edits three marker blocks: the includes in `settings.gradle.kts`,
the host dependencies in `app/build.gradle.kts`, and one registration line in the host Application's
`registerFeatures()`. Exit code 2 means a file already existed and differed — the new version sits next to it
as `<file>.arch-spec-new`; compare, merge, delete the `.arch-spec-new` file.

The build must be green **before** you change anything; the template compiles and its tests pass.

## 3. Wire it into the host

- **Registration** is already added. Each host dependency line in `app/build.gradle.kts` gets a short
  comment saying why the host needs it (MOD-14).
- **Entered from a sibling?** The sibling's FeatureImpl gets a nullable constructor seam with a `null`
  default (`newRoute: String? = null` or `((id: String) -> String)? = null`), and the host fills it at
  registration with this feature's route constant or builder. Never add a dependency from one feature to
  another (MOD-11, MOD-18).
- **Returns a result?** The key is owned by the screen that reads it, published in the reader's contract
  module, handed to the writer by the host (UI-21).
- **Domain needs a sibling's capability?** Declare a small `fun interface` port in this feature's domain,
  leave it unbound (say so in the domain module KDoc), and bind it in a host DI module (MOD-19).
- **Start destination?** Change the NavHost's start route in the host only when asked.

## 4. Build the first screen

Replace the template's placeholder content, keeping its structure:

- contract file: notice / failure enums, row Ux models, the sealed Ux state, UiCallbacks, intents;
- ViewModel: private State with every field defaulted, one `toUxState()` projection, guards on intents,
  load on entry + re-read on resume, one notice slot;
- screen: stateful entry + private stateless body + previews per state at the reference device;
- domain: interactor interface + default implementation returning a sealed outcome; services only behind an
  interface; DI module in the domain module;
- strings in the feature's own resources, prefixed with the feature id.

Ask the developer for the visual spec (design frame, copy, states) before styling; use only theme tokens and
kit components.

## 5. Test

- ViewModel test with the fake interactor (template provides one): every outcome projected, every intent,
  navigation through a recording UiCallbacks, double-submit guard.
- Domain tests for every non-trivial class.
- Run `./gradlew assembleDebug testDebugUnitTest` and record the counts in the task log.

## 6. Record it

- Add the three modules to the module list in `CLAUDE.md` (inside its modules marker block, one line each in
  the existing format: module, package, one-line purpose) — MOD-22.
- Run the **skills-maintenance** capture checklist: a new seam type, a new kit component, a new screen shape
  or a decision belongs in a skill (or an ADR in the `.docs` decisions folder), not only in code.
- `bash tools/skills/lint.sh && bash tools/skills/check-refs.sh` must pass.

## Removing a feature

Delete its three module directories, its include / dependency / registration lines, every seam the host
filled with its routes, and its lines in `CLAUDE.md`; then build. Mention in the skills any rule that named it.
