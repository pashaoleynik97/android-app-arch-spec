# ADR-0010 — Convention plugins in an included build over shared Gradle scripts

Status: accepted · Area: BUILD-01..BUILD-09

## Context

Shared module configuration can live in `apply(from = "common.gradle")` script plugins, in `buildSrc`, in
`subprojects {}` blocks, or in convention plugins contributed by an included build. Scripts are untyped and not
cached; `subprojects {}` is cross-project configuration that breaks isolation and the configuration cache;
`buildSrc` invalidates the whole build on any change.

## Decision

Shared configuration lives in `build-logic`, an included build (`pluginManagement { includeBuild("build-logic") }`)
contributing typed convention plugins by id (`{{CONVENTION_PREFIX}}.android.library` and siblings), sharing the
root version catalog, with tooling dependencies `compileOnly`. Module build files only apply conventions and
declare a namespace and dependencies.

## Consequences

- Module build files stay a few lines; a toolchain change is a one-place edit.
- Conventions are compiled and config-cache friendly; the catalog accessor inside plugin code is string-based
  (`findLibrary`) and must stay `internal`.
- Adding a convention requires a class, a registration and a catalog marker (BUILD-09).
