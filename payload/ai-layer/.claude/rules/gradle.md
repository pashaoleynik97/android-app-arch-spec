---
paths:
  - "**/*.gradle.kts"
  - "gradle/libs.versions.toml"
  - "build-logic/**"
---

# Gradle files

Owner: the **application-architecture** skill, build-system and toolchain references (load them before a
non-trivial edit). The rules most often broken here:

- Every version lives in `gradle/libs.versions.toml` and is referenced as `libs.*`; a pinned version carries its
  reason as a comment directly above it (BUILD-10, BUILD-11).
- A module build file is declarative: convention plugins by alias, `namespace`, `dependencies {}`. Never set
  what a convention owns — SDK levels, Java target, Compose flags or BOM, Hilt deps, test defaults (BUILD-04).
- `.compose` conventions are listed after the base `.android.library` / `.android.application` (BUILD-05).
- Never apply the standalone Kotlin Android plugin on AGP with built-in Kotlin (BUILD-30).
- The configuration cache stays on: read files and properties only through `providers` (BUILD-14, BUILD-41).
- A module that declares any injected type applies the hilt convention (DI-05). Feature-trio modules apply
  their role convention (`feature.api` / `feature.ui` / `feature.domain`) and add only their own edges (BUILD-03).
- A red `checkModuleGraph` names a module rule (MOD-…): fix the edge, never `ModuleGraphRules` (BUILD-24).
- Lint and ktlint are configured in the conventions and `.editorconfig`; never disable a check or add to
  `app/lint-baseline.xml` to get green — fix the code, or suppress one line with a comment saying why (BUILD-25, BUILD-26).
- A new module completes the whole new-module checklist in the same change (MOD-22).
- Every dependency line in the host build file says why the host needs it (MOD-14).
- A changed convention, new convention or toolchain gotcha updates the build-system / toolchain reference in
  the same change.
