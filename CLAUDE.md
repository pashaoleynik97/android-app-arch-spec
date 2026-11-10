# android-app-arch-spec — authoring guide (for agents editing THIS repo)

> **Installing into a project?** This file is not for you: you are reading the spec's copy under `.arch-spec/spec/`.
> Follow `INSTALL.md` and the project's own `CLAUDE.md`, and never edit files under `.arch-spec/spec/`.

This repo is a **specification + installer payload** that rolls an opinionated multi-module Android architecture
(Kotlin, Compose, Hilt, Gradle convention plugins), test tooling, CI/CD and an AI-collaboration workflow into a
freshly created Android Studio project. This file is never copied into a target project.

## Layout (what goes where)

| Path | Purpose |
|---|---|
| `INSTALL.md` | The entry prompt an agent follows inside a target project. Agent-agnostic. |
| `README.md` | For humans: what it is, quick start, components, known-good toolchain. |
| `installer/` | `parameters.md` (every placeholder), `components.yml` (ids, tiers, deps, gates, marker inserts), `questionnaire.md`, `components/<id>.md` (per-component install procedure), `render.py` (deterministic renderer), `gates/*.sh`. |
| `tests/fixtures/empty-activity/` | Replica of Android Studio's stock "Empty Activity" project — the starting point every dry run renders into. |
| `specs/` | The normative spec. Core = **MUST** rules, Recommended = **SHOULD** guidance. Stable rule IDs. |
| `payload/<component>/` | Files laid into the target. The tree **mirrors the target project root** exactly. |
| `skills/` + `.claude-plugin/` | Thin Claude Code plugin wrapper (`/android-arch:install`, `/android-arch:doctor`) that defers to `INSTALL.md`. |

## Tiers and wording

- **Core** components (`foundation`, `ai-layer`, `architecture` — incl. design system + test kit — and `docs`) apply
  to any app.
  Their specs use **MUST / MUST NOT** (RFC 2119), each rule with an ID and a one-line *why*.
- **Recommended** components (everything else in `installer/components.yml`) depend on the app's requirements.
  Their specs use **SHOULD / MAY** and open with "When to use / when not to". Reference code is offered, never forced.
- Toolchain versions and backend contracts are **recommendations** ("last known-good set", "example contract").

## Rule IDs

`<AREA>-<NN>`: `BUILD`, `MOD`, `DI`, `KERNEL`, `UI`, `DS` (design system), `DATA`, `TEST`, `CI`, `AI`, `SKILL`,
and for Recommended components `NET`, `AUTH`, `CONN`, `PAGE`, `ACCESS`, `IMG`, `MEDIA`, `INPUT`, `ENG`, `MITM`, `VERIFY`.
IDs are never renumbered or reused; a removed rule stays as `~~ID~~ (removed in vX.Y)`.

## Placeholders & rendering

- Syntax `{{UPPER_SNAKE}}`, in file **contents and paths**. Every placeholder MUST be defined in
  `installer/parameters.md`. `installer/render.py` substitutes them; nothing else may.
- A payload file without placeholders is copied verbatim.
- Kotlin sources live under `.../src/main/java/{{ROOT_PACKAGE_PATH}}/<module-segment>/...` (or
  `{{APP_PACKAGE_PATH}}` for `:app`).
- Feature-scoped templates (`payload/architecture/feature-template/`) additionally use `{{FEATURE}}` (kebab),
  `{{FEATURE_PASCAL}}`, `{{FEATURE_CAMEL}}`, `{{FEATURE_PKG}}` (package segment, lowercase, no dashes).
- **Placeholders must render to code that compiles.** Deferred answers render to safe placeholder values
  (see `parameters.md`), never to something that breaks `assembleDebug`.

## Content rules

1. **Domain-neutral.** No business domain, no company/product names, no ticket numbers, no non-English text
   (except as clearly-marked locale examples). `installer/gates/domain-leak.sh` enforces English-only text plus a
   maintainer-local deny-list (`installer/gates/.domain-leak-denylist`, git-ignored — never commit it).
2. **Self-contained.** The spec never refers to any other project as its source. Rationale is stated directly.
3. **Why with every rule.** A rule without a reason gets "fixed" by the next agent.
4. **No version numbers in prose** — only in `payload/foundation/gradle/libs.versions.toml` and
   `specs/toolchain.md`. Gotchas are written version-conditional ("on AGP ≥ 9 …").
5. **Size budgets.** Any `SKILL.md` ≤ 300 lines (hard cap 500) with detail in `references/` one level deep;
   any spec file ≤ ~600 lines (split otherwise); `CLAUDE.md` templates ≤ 150 lines.
6. Code in `payload/` is real, compiling, tested code — with KDoc that explains *why*, matching the density of the
   rest of the payload. Every non-trivial class ships with a JVM unit test.
7. English only.

## Self-checks before declaring work done

```
installer/gates/domain-leak.sh .            # English-only + your local deny-list (git-ignored)
installer/gates/skills-lint.sh payload       # frontmatter, size budgets, name == folder
python3 installer/render.py --self-check     # every placeholder used is defined; components.yml paths exist
```
