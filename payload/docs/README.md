# {{APP_NAME}}

<One or two paragraphs: what the app does, for whom, on which devices. Fill this in.>

Multi-module Android app — Kotlin, Jetpack Compose, Hilt, Gradle convention plugins.

## Run it

Requirements: the JDK and Android SDK platform named in `.arch-spec/spec/specs/toolchain.md` (the compile SDK
is set in `build-logic/convention/src/main/kotlin/ProjectConfig.kt`).

```
./gradlew assembleDebug          # build the debug APK
./gradlew installDebug           # install it on a connected device / emulator
./gradlew testDebugUnitTest      # all JVM unit tests (the CI gate)
```

Versions live only in `gradle/libs.versions.toml`. The version name lives in `version.properties`; the
version code is derived from it.

Release signing: copy `keystore.properties.template` to `keystore.properties` (gitignored) and point it at the
keystore. Without it, release builds fall back to the debug key — they build, but cannot be published.

## Modules

The module table (module · package · purpose) and the dependency direction are kept in one place:
[`CLAUDE.md`](CLAUDE.md), section "Modules". In short: the host `:app` aggregates features; every UI feature
is three modules (contract `x-ui-api`, UI `x-ui`, domain `x`); features never depend on each other; the kernel
(`:lib-common`, `:lib-android`, `:feature-api`) never depends on a feature.

## Architecture

The rules and their reasons live in the project's skills (read by people and AI agents alike):

| Topic | Where |
|---|---|
| modules, build system, toolchain, DI, kernel, ViewModels and navigation, data and errors, testing | `.claude/skills/application-architecture/` (SKILL.md + references) |
| design system, UI kit, host chrome, screen layout | `.claude/skills/compose-ui/` |
| adding a feature | `.claude/skills/add-feature/` |
| how the skills themselves change | `.claude/skills/skills-maintenance/` |
| shipped behaviour explained for product, QA and developers | [`.docs/`](.docs/README.md) |
| decisions and their reasons | `.docs/decisions/` |

The architecture was installed from a spec kept in `.arch-spec/spec/`; `.arch-spec/` also holds the install
state and the pristine baseline used for upgrades (committed; never edit the baseline by hand).

**Keep it true:** a change that alters a convention updates the owning skill (and the `.docs` page, if one
describes the behaviour) in the same commit.

## Testing

- JVM unit tests for every non-trivial class, with hand-written fakes (no mocking library); shared kernel test
  doubles in `:core-testing`.
- `./gradlew :<module>:testDebugUnitTest` while iterating; `./gradlew testDebugUnitTest` before pushing.
- Device / emulator verification against scripted server responses is available when the mitm-testing
  component is installed, and runs on request.

## CI/CD

<If the ci-cd component is installed: pipelines in `.github/workflows/`, one-time setup in `ci_runbook.md`,
how QA installs builds in `docs/qa-installing-builds.md`. Otherwise: describe how this project is built and
released.>

## Working with AI agents

`CLAUDE.md` is the entry point for coding agents: it indexes the skills and the build commands. Tasks follow
`.claude/workflow/general.md` (analyse → ask → plan → approval → implement → update skills); per-task working
folders under `.claude/no-commit-files/` are excluded from git locally. Skill health:
`bash tools/skills/lint.sh && bash tools/skills/check-refs.sh`.

## Deferred setup

Items skipped at install time (design palette and font, backend URLs, signing, …) are listed with hints by:

```
python3 .arch-spec/spec/installer/render.py status --target .
```

Complete them as described in `.arch-spec/spec/INSTALL.md`, "Complete deferred items".
