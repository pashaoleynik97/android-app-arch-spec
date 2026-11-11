# ADR-0011 — Keep string routes; type-safe `@Serializable` routes rejected

Status: rejected (the proposal to adopt type-safe routes) · Area: UI-18..UI-21, MOD-09, MOD-18

## Context

UI-19 declares routes as string templates and builder functions in `object XRoutes` inside `:x-ui-api`. Argument
names, types and optionality are therefore checked at runtime, not by the compiler. navigation-compose on the
known-good set also offers type-safe routes: `@Serializable` route classes, `composable<Route>`,
`navController.navigate(Route(id))`, `backStackEntry.toRoute<Route>()` and `SavedStateHandle.toRoute<Route>()`.
This is not Navigation 3 (out of scope, `toolchain.md`); it would be a change of route style within the same
library.

The proposal: `:x-ui-api` exposes `@Serializable` route types instead of string constants, `XFeatureImpl` uses
`composable<XRoute>`, ViewModels read arguments with `savedStateHandle.toRoute<XRoute>()`; back-stack results
(UI-21, ADR-0004) stay as they are.

## Decision

Rejected. Routes stay strings, shaped by UI-19.

1. **Contracts stay minimal (MOD-09).** Every `:x-ui-api` would need the kotlinx-serialization compiler plugin
   and runtime. Serialization is today a Recommended (`network`) dependency; this would make it Core and put it
   on the compile path of every contract, the host and every sibling that links to a feature.
2. **Host seams lose their type (MOD-18).** A caller must not know the callee's vocabulary, so a seam carries a
   route without naming the callee's route class: today `((id: String) -> String)?`, with type-safe routes
   `((id: String) -> Any)?`. The compile-time guarantee does not reach across features, which is where an
   argument mismatch is hardest to spot.
3. **Route shapes stay explicit.** The string form is what deep links, `adb shell am start` scenarios and the
   back-stack inspector show. With serialized classes the shape is derived from the class and changes silently
   when a property is renamed.
4. **Migration cost.** The feature template, the host `NavHost`, `auth-session` (sign-in route and re-rooting on
   session end), `engineering-menu`, the MOD-18 seam types, `patterns/` and every installed project's features
   would change, for a class of bugs UI-19 already contains.

## Consequences

- The runtime risk is contained by UI-19 instead of the compiler: argument names are `const val ARG_*` in
  `XRoutes`, shared by the registered template, the builder and every reader (`entry.arguments`,
  `SavedStateHandle`); no string literal names an argument anywhere else. A renamed argument is one edit in one
  file.
- `toolchain.md` keeps "string routes"; this ADR is the reference when an agent proposes the switch again.
- Revisit when (a) a project adopts Navigation 3, whose keys are typed by design, or (b) kotlinx-serialization
  becomes a Core dependency for another reason, which removes reason 1. A revisit supersedes this ADR with a new
  one; UI-19..UI-21 then change with removed IDs kept, per the ID policy.
