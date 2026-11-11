# ADR-0012 — Module rules enforced by a convention task, not a third-party plugin

Status: accepted · Area: BUILD-24, MOD-01, MOD-07, MOD-09, MOD-10, MOD-11, MOD-19

## Context

The dependency direction (MOD-01) and the absence of edges between features (MOD-11) carry the architecture,
but they were prose only: in `specs/10-modules.md` and in the installed skill. Coding agents break prose rules;
they do not ignore a red build. Options considered:

- **dependency-guard** style baselines snapshot each module's dependency list and fail on any change. They catch
  drift, but express no direction: an illegal edge is accepted once the baseline is updated, which is exactly
  what an agent does to get green.
- **Module-graph assertion plugins** express layer rules, but need a per-project rule configuration that
  duplicates MOD-20 naming, add a third-party plugin to the build classpath, and their configuration-cache
  support follows their own release cycle.
- **A small task in the existing conventions.**

## Decision

The library and application conventions register `checkModuleGraph` on every module (BUILD-24). A pure
`ModuleGraphRules` object classifies modules by their MOD-20 names and checks each declared project edge; the
task collects the edges as strings while the project is configured and never touches `Project` at execution
time. Rules are unit-tested in `build-logic`.

## Consequences

- Every installed project gets the check with no build-file line, and a new rule is one function plus a test.
- Classification depends on names: a module that breaks MOD-20 is misjudged. The remedy is renaming it, which
  MOD-20 already requires.
- Only **declared** project edges are checked (`implementation`, `api`, `testImplementation`, …); a conditional
  edge (e.g. an optional component's `if (hasX) implementation(project(":x"))`) is checked when the condition
  holds. Transitive exposure through `api` is not re-checked; MOD-02 keeps `api` rare and commented.
- A legitimate exception is an ADR plus a reviewed change to `ModuleGraphRules`, never a local suppression.
