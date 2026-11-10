# ADR-0003 — Explicit feature registration; multibinding as the scale path

Status: accepted · Area: MOD-18, DI-15, UI-18

## Context

Features attach their destinations to the single `NavHost` through `FeatureApi.registerGraph`. They must be
collected somewhere. Options: Hilt multibinding (`@IntoSet FeatureApi` in each `:x-ui`), or explicit calls in the
host's `Application.onCreate()`. Most features also need **host-supplied seams** (routes and result keys of
siblings, MOD-18), which only the host can fill.

## Decision

The host registers features **explicitly**:
```kotlin
private fun registerFeatures() {
    HomeFeatureImpl(settingsRoute = SettingsRoutes.MAIN).addToRegistry()
    SettingsFeatureImpl().addToRegistry()
}
```
`FeaturesHolder` keeps them in insertion order, de-duplicated by concrete class; tests call `clear()`.

## Consequences

- Seams are visible in one place; a missing seam is a reviewable `null`, not a hidden DI binding.
- A new feature needs one line in the host (installer marker `feature-registrations`).
- **Scale path:** when the number of features makes the list unwieldy, switch to `@IntoSet FeatureApi` with
  seams supplied through injected host-provided link objects; record that as a new ADR superseding this one.
- `FeaturesHolder` is a global mutable registry; instrumented tests must clear it between runs.
