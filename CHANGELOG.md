# Changelog

Versions follow semver. **MAJOR** changes require manual migration notes, **MINOR** adds components or rules, and
**PATCH** fixes the spec or payload without changing any rule. Each entry lists what changes in an installed project
and how the **upgrade** procedure (INSTALL.md §7) handles it.

## 0.2.0 — build-enforced module rules, feature conventions, lint and formatting, spec CI

Upgrade (INSTALL.md §7): automatic for every file the project has not modified; modified files get the usual
three-way merge. Old convention plugin ids keep working, so feature modules created before this version build
unchanged; switching them to the new role conventions is optional.

**New rules and decisions**
- BUILD-24 (new) — `checkModuleGraph` on every module, hooked into `check`; fails with the rule id on edges that
  break MOD-01, MOD-07, MOD-09, MOD-10, MOD-11 or MOD-19. MOD-01 and MOD-11 reference it. ADR-0012 records why it
  is a convention task and not dependency-guard or a third-party graph plugin.
- BUILD-25 (new) — Android Lint defaults in the conventions; `:app` checks dependencies and owns
  `app/lint-baseline.xml` (rendered empty; findings are fixed, never baselined).
- BUILD-26 (new) — ktlint through Spotless on `src/**/*.kt` and module `*.gradle.kts`, configured by a root
  `.editorconfig`; `spotlessCheck` is part of `check`.
- BUILD-03 / BUILD-04 / BUILD-09 — new role conventions `{{CONVENTION_PREFIX}}.feature.api`, `.feature.ui`,
  `.feature.domain`; convention ids may be `…android.<name>` or `…feature.<role>`; ids are never removed.
- MOD-09, MOD-22 — the contract module uses `feature.api`; the checklist names the role conventions.
- UI-19 — arguments are named only through `ARG_*` constants; type-safe `@Serializable` routes were evaluated and
  rejected (ADR-0011). No other navigation rule changed.
- CI-02 (Recommended) — the PR gate also runs `spotlessCheck checkModuleGraph :app:lintDebug`.
- `toolchain.md` — new rows: Spotless Gradle plugin, ktlint, compose-lints (catalog only); navigation row points
  at ADR-0011.

**What changes in an installed project**
- `foundation`: new `build-logic` sources (`AndroidFeature{Api,Ui,Domain}ConventionPlugin.kt`, `CodeQuality.kt`,
  `ModuleGraphRules.kt`, `CheckModuleGraphTask.kt`, `src/test/kotlin/ModuleGraphRulesTest.kt`), library /
  application conventions apply lint, Spotless and the module-graph check; `build-logic/convention/build.gradle.kts`
  (`compileOnly` Spotless plugin, `testImplementation` JUnit, three registrations); catalog (`spotless`, `ktlint`,
  `composeLints` versions, `spotless-gradlePlugin`, `compose-lints`, `spotless` plugin, three `-feature-*` markers);
  root `build.gradle.kts` (`spotless` `apply false`); new `.editorconfig`, `app/lint-baseline.xml`;
  `app/build.gradle.kts` reformatted (no behaviour change).
- `architecture`: feature template build files (and so every **newly scaffolded** feature) use the role
  conventions; `home` is rendered from it, so an unmodified `home*` build file is upgraded, a modified one keeps
  its plugin list (still valid). `BaseViewModel.onCleared` no longer calls the empty `super` (lint EmptySuperCall).
  Kotlin formatting of several kit previews and theme accessors.
- `auth-session`: `login*` build files use the role conventions; the token and profile stores use the KTX
  `edit(commit = true) { }` (same synchronous behaviour, lint-clean); formatting.
- `engineering-menu`: `engineering-ui*` build files use the role conventions.
- `hardware-input`: `:input` adds `implementation(libs.androidx.core.ktx)`; the settings store uses `edit { }`.
- `access-loss`, `network`, `paging`: formatting only (sources and tests).
- `ai-layer`: `CLAUDE.md` build commands (`spotlessApply`, `spotlessCheck checkModuleGraph :app:lintDebug`) and one
  non-negotiable; `.claude/workflow/general.md` and `templates/verification.md` verify step; `.claude/rules/gradle.md`;
  `tools/skills/skills_tool.py` (check-refs) understands `includeBuild(...)` projects such as
  `:build-logic:convention`. No `.claude/settings.json` change.
- `ci-cd`: `pr-build.yml` runs the quality gates and uploads lint reports on failure; runbook, PR template.
- Skill references: `build-system.md` (role conventions, BUILD-24..26, recipes), `modules.md`, `feature-trio.md`,
  `presentation.md` (UI-19), `ci-cd.md`; `application-architecture/SKILL.md` (MOD-01, MOD-11);
  `add-feature/SKILL.md`.
- No renamed or moved rendered paths. No new placeholders.

**Gates**
- `foundation`: `./gradlew :build-logic:convention:test`, `./gradlew :app:assembleDebug checkModuleGraph` (no
  `spotlessCheck` or lint yet: the stock sources, replaced by `architecture`, are neither formatted nor, with a
  non-stock `APP_PACKAGE`, consistent with the manifest).
- `architecture`: `./gradlew assembleDebug checkModuleGraph`, `./gradlew testDebugUnitTest`,
  `./gradlew spotlessCheck :app:lintDebug`.
- Upgrading a project that has its own code: run `./gradlew spotlessApply` once and commit it on its own; existing
  lint findings in the project's own code may be baselined once (`:app:updateLintBaseline`), with review.

**Spec repository**
- `.github/workflows/spec-ci.yml`: render self-check, English-only leak check, skills lint, and the `startup`
  install smoke on every PR and push to `main`; `backend-app` and `long-names` on manual dispatch.
- `tests/smoke/install-smoke.sh` (`startup` | `backend-app` | `everything` | `long-names`), `render.py manifest` (read-only), and
  the MAINTAINING.md dry-run section that describes them.

## 0.1.0 — initial

- Core components: `foundation`, `ai-layer`, `architecture` (incl. design system and test kit), `docs`.
- Recommended components: `ci-cd`, `mitm-testing`, `network`, `auth-session`, `access-loss`, `connectivity`,
  `paging`, `images`, `media-store`, `hardware-input`, `engineering-menu`.
- `installer/render.py`: deterministic rendering (contents and paths), checksums with `.arch-spec-new` conflict
  files, deferred items with how-to lines, marker-block inserts, `snapshot` / `rebase` for three-way upgrades,
  `relocate` for renamed modules, recorded install order.
- Gates: `no-placeholders-left`, `skills-lint`, `test-summary`, `workflows-check`, and the maintainer-only
  `domain-leak`.
- Project skill tooling: `tools/skills/{lint,check-refs}.sh`, and `skills-maintenance` procedures (amend, capture,
  audit, hygiene, new-skill, upgrade, rename-module).
- Verified by three agent-driven dry runs (startup, backend app + add component, skill evolution + upgrade) and a
  full install of every component.
