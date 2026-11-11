# android-app-arch-spec

A specification and an installer that an AI coding agent uses to turn a freshly created Android Studio
"Empty Activity" project into a production-shaped one. The result has:

- **Build system**: a Gradle `build-logic` composite with convention plugins (including one per feature-module
  role), a version catalog as the only place versions live, configuration cache on, CI-owned versioning and a
  signing chain. The build enforces the module dependency rules (`checkModuleGraph`), ktlint formatting through
  Spotless, and Android Lint.
- **Architecture**: a layered multi-module app (`:app → :lib-android → :feature-api → :lib-common`). Every feature is
  three modules (`-ui-api` contract / `-ui` Compose / domain). Hilt DI throughout. ViewModels follow a strict
  State → UxState contract, and host-owned chrome (top bar, snackbars) is driven declaratively by screens.
- **Design system**: a token-based Compose theme (colours, typography, dimensions) with a base UI kit. It works
  with a placeholder palette until the real design exists.
- **Testing**: JVM unit tests with hand-written fakes and coroutine virtual time. Optionally, a mitmproxy scenario
  library, a strict fake backend, and on-demand, evidence-based verification on an emulator.
- **CI/CD** (optional): four GitHub Actions pipelines covering PR checks, publish on merge with an automatic version
  bump, a manual release trigger, and GitHub Releases.
- **AI layer**: a thin `CLAUDE.md`, project-owned skills that are the source of truth for conventions, a task
  workflow (analysis → questions → plan → approval → implement → verify), and tooling that keeps the skills current
  as the project evolves.

## Quick start

1. Create a new project in Android Studio: *Empty Activity*, Kotlin DSL build.
2. Copy this repository into it as `.arch-spec/spec/`, leaving out `.git/` and IDE folders. A git submodule works
   too.
   ```bash
   mkdir -p .arch-spec && rsync -a --exclude .git --exclude .idea --exclude tests/fixtures \
     /path/to/android-app-arch-spec/ .arch-spec/spec/
   ```
   The copy is committed with the project, because later sessions, CI and upgrades use it.
3. Ask your agent:
   > Follow `.arch-spec/spec/INSTALL.md`.

   With Claude Code you can instead add this repo as a plugin marketplace and run `/android-arch:install`.
4. Answer what you know and say *skip* to the rest. Anything you skip, such as brand colours, a backend URL or
   signing, gets a safe placeholder that builds. It is listed as **deferred**, so you can complete it later:
   > The design is ready: primary colour #0A84FF, font Inter. Complete the deferred design items.

The installer runs one component at a time. Each must pass `./gradlew assembleDebug testDebugUnitTest` (plus that
component's own checks) and is committed separately on the `arch-spec/install` branch.

## Components

| Component | Tier | What you get |
|---|---|---|
| `foundation` | Core | build-logic conventions (platform + feature roles), module-graph check, lint + ktlint defaults, `.editorconfig`, catalog, gradle.properties, versioning + signing in `:app` |
| `ai-layer` | Core | `CLAUDE.md`, skills (architecture, compose-ui, add-feature, skills-maintenance), rules, settings, task-workflow templates, skill lint/ref-check tools |
| `architecture` | Core | `:lib-common`, `:feature-api`, `:lib-android` (theme, host chrome, base kit), `:core-testing`, `:app` host, reference feature `home` |
| `docs` | Core | README and architecture-doc skeletons |
| `ci-cd` | Recommended | GitHub Actions pipelines, runbook, PR template |
| `mitm-testing` | Recommended | `.mitm_scripts/` scenario library, strict fake backend, emulator helper, verification skills |
| `network` | Recommended | qualified OkHttp/Retrofit client, response envelope (example contract), classified sealed results |
| `auth-session` | Recommended | encrypted token store, single-flight refresh, authenticator, activity-gated keep-alive, session end → sign-in, a minimal sign-in feature |
| `access-loss` | Recommended | 403 chain-of-responsibility bus with a host fallback |
| `connectivity` | Recommended | network-state watch (link + server health) with a host banner; works without `network` |
| `paging` | Recommended | generation-guarded paging engine + Compose scroll trigger and footers |
| `images` | Recommended | `:files` (file URLs + authenticated client) and `:images` (Coil loader, remote image / avatar composables) |
| `media-store` | Recommended | app-internal scratch media with TTL sweep |
| `hardware-input` | Recommended | pluggable vendor-driver pattern + debug driver injected via adb |
| `engineering-menu` | Recommended | hidden diagnostics menu with pluggable sections contributed by other components |

Optional components plug into the host through Hilt multibindings (`HostExtension`, `HostActivityObserver`), so Core
never references them, and adding or removing one never edits Core code.

**Core** components are rules (MUST). **Recommended** components are advice (SHOULD): each one's spec opens with
*when to use / when not to*, and nothing is installed unless you say yes.

## Known-good toolchain

`specs/toolchain.md` holds the last combination verified end to end: Gradle, AGP, Kotlin, KSP, Hilt, Compose BOM,
SDK levels. It also lists the version-specific traps. It is a recommendation: ask for *latest* and the installer
upgrades, then re-verifies every trap.

## Repository map

| Path | For |
|---|---|
| `INSTALL.md` | the agent's entry prompt: fresh / add / complete-deferred / add-feature / upgrade / doctor |
| `specs/` | the normative spec with stable rule IDs (`specs/README.md` has the index) |
| `installer/` | parameters, component manifest, questionnaire, per-component procedures, renderer, gates |
| `payload/` | the files laid into a project, one directory per component |
| `tests/fixtures/` | a stock empty project used by the dry runs |
| `tests/smoke/` | `install-smoke.sh`: scripted startup / backend-app / everything dry runs (CI runs startup on every PR) |
| `.github/workflows/` | `spec-ci.yml`: self-check, lints and install smoke for this repository |
| `MAINTAINING.md` | how to change the spec safely and how to run the dry runs |

## After installation

The skills in `.claude/skills/` belong to your project. The `skills-maintenance` skill handles:

- **amending** a rule when your conventions change;
- **capturing** new patterns at the end of a task;
- **auditing** skills against the code for drift;
- **upgrading** to a newer version of this spec with a three-way comparison that never overwrites your own changes
  or your recorded **Project overrides**.

## License

MIT. See [LICENSE](LICENSE).
