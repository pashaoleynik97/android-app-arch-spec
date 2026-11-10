# {{APP_NAME}}

Multi-module Android app — Kotlin, Jetpack Compose, Hilt, Gradle convention plugins in an included
`build-logic` build. Every module's package lives under `{{ROOT_PACKAGE}}`; convention plugins are
`{{CONVENTION_PREFIX}}.android.*`; the design system is **{{THEME_PREFIX}}Theme**. The architecture was rolled in
from a spec whose copy, install state and pristine baseline live in `.arch-spec/` (committed; never edit
`.arch-spec/baseline/` by hand).

## Architecture & conventions

Skills are the source of truth; this file only points at them. When a pointer here and a skill disagree, the
skill wins and this file is fixed in the same change.

- **application-architecture** — module layout and dependency direction, the build system (convention plugins,
  version catalog, toolchain gotchas), DI, the kernel (logging, flow state, seams), presentation (ViewModel
  contract, navigation), data and errors, testing, and every installed infra component. Load it before adding a
  module, wiring DI, editing Gradle files, or writing domain / ViewModel code.
- **compose-ui** — the design system (**{{THEME_PREFIX}}Theme** colors / typography / dimens), the base UI kit,
  host chrome (app bar, banners) and screen layout. Load it before building or styling any screen or preview.
- **add-feature** — scaffolds a new feature (three modules), wires it into the host, writes its first screen.
  Load it whenever asked to add a feature.
- **skills-maintenance** — the only way skills change: amend, capture, audit, hygiene, new skill, upgrade from a
  newer spec. Load it when a convention changes, when a task ends with a new pattern, or when skills look stale.

Installed components (one line each; components add their own line on install):
<!-- <arch-spec:components> -->
<!-- </arch-spec:components> -->

## Modules

One line per module — `module` `package` — purpose (components and the add-feature skill add lines here):
<!-- <arch-spec:modules> -->
<!-- </arch-spec:modules> -->

Dependency direction (never invert; nothing depends on the host):
```
:app ──▶ :x-ui ──▶ :x-ui-api ──▶ :feature-api ──▶ :lib-common
  │        ├────▶ :x (domain) ─────────────────────▶ :lib-common
  │        └────▶ :lib-android ──▶ :feature-api, :lib-common
  └──▶ infra modules ──▶ :lib-common          (:core-testing: testImplementation only)
```

**Every UI feature is three modules** — `x-ui-api` (contract: routes + marker interface), `x-ui` (screens,
ViewModels), `x` (domain, no Compose). Features never depend on each other; the host wires them together.
A trivial feature may omit the domain module. Use the **add-feature** skill to create one.

## Build / verify

```
./gradlew assembleDebug                           # full build (configuration cache is on)
./gradlew testDebugUnitTest                       # all JVM unit tests — the CI gate
./gradlew :<module>:testDebugUnitTest             # one module while iterating
bash tools/skills/lint.sh && bash tools/skills/check-refs.sh    # AI-layer health (skills, rules, this file)
python3 .arch-spec/spec/installer/render.py status --target .   # installed components, deferred items
```

## Non-negotiables (details and reasons in the skills)

- Versions only in `gradle/libs.versions.toml`; shared build config only in `build-logic` convention plugins;
  module build files are declarative.
- On AGP with built-in Kotlin never apply the standalone Kotlin Android plugin; the configuration cache stays
  on, so build scripts read files only through `providers`.
- Log only through the kernel Reporter (`where = logTag`); never `android.util.Log` elsewhere; never log
  credentials.
- Every screen: Hilt ViewModel + thin base class + a per-screen contract interface; navigation code only in
  the feature's FeatureImpl.
- Style only from **{{THEME_PREFIX}}Theme** tokens; no hardcoded colors, sizes or user-visible text.
- Every non-trivial class gets a JVM unit test with hand-written fakes; no mocking library.

## Working on a task

- Follow `.claude/workflow/general.md`: analyse → ask explicitly → plan → implement only after explicit
  approval → update the owning skill in the same change. Templates: `.claude/workflow/templates/`.
- Questions for a non-developer decision maker follow `.claude/workflow/ba-round-rules.md`.
- Task folders live in `.claude/no-commit-files/<task>/`, excluded through `.git/info/exclude`. Committed files
  never point into them.
- Unit tests are the default verification; device / emulator verification runs only when explicitly asked.
- Never commit, push or open a pull request unless asked; then work on a branch, never on the default branch.

## Deferred items

Questions skipped at install time (design, backend URLs, signing …) rendered safe placeholders. They are listed
under `deferred` in `.arch-spec/state.json`; `render.py status` (above) prints them with a hint. Completing
one follows `.arch-spec/spec/INSTALL.md`, section "Complete deferred items".

## Keeping the AI layer true

- A change that extends or alters a convention updates the owning skill (and the `.docs` page describing the
  behaviour, if any) **in the same change**, with a `CHANGELOG.md` entry next to that skill's `SKILL.md`.
- Skills change only through the **skills-maintenance** procedures; spec rules are overridden with a recorded
  Project override block, never edited silently.
- Before declaring a task done, run the capture checklist from **skills-maintenance**.
