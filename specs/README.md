# specs/ — index and reading order

The normative specification. **Core** files use MUST / MUST NOT (RFC 2119), every rule has a stable ID and a
one-line *why*. **Recommended** files (`recommended/`, `patterns/`) use SHOULD / MAY. Rule IDs are never
renumbered or reused; a removed rule stays as `~~ID~~ (removed in vX.Y)`.

Notation: `{{UPPER_SNAKE}}` = an installer parameter (`installer/parameters.md`); `<Prefix>` = the
`{{THEME_PREFIX}}` parameter in prose (`<Prefix>Theme`, `<Prefix>Button`); `x` / `X` = any feature id.

## Reading order

1. `00-constitution.md` — the one-page non-negotiables, each pointing at its rule.
2. `10-modules.md` — what modules exist and which way dependencies point.
3. `20-build-system.md`, then `toolchain.md` — how the build is put together; versions and gotchas.
4. `30-di.md` — Hilt rules.
5. `40-kernel.md` — `Reporter`, `Scopes`, foreground watch, seams, main-safety.
6. `50-presentation.md` and `55-design-system.md` — ViewModels, screens, navigation, host chrome; tokens and kit.
7. `60-data-and-errors.md` and `70-testing.md` — domain layer, error model, tests.
8. `90-ai-workflow.md` — how agents work in the target project and how its skills evolve.
9. `decisions/` when a rule looks surprising; `patterns/` when building a screen of a known shape;
   `recommended/` before installing an optional component.

## Rule-ID index (Core)

| File | Area prefix | IDs |
|---|---|---|
| `00-constitution.md` | — | articles 1–23 (point to rules; define none) |
| `10-modules.md` | `MOD` | MOD-01 … MOD-22 |
| `20-build-system.md` | `BUILD` | BUILD-01 … BUILD-26 |
| `toolchain.md` | `BUILD` (gotchas) | BUILD-30 … BUILD-44 |
| `30-di.md` | `DI` | DI-01 … DI-16 |
| `40-kernel.md` | `KERNEL` | KERNEL-01 … KERNEL-20 |
| `50-presentation.md` | `UI` | UI-01 … UI-32 |
| `55-design-system.md` | `DS` | DS-01 … DS-26 |
| `60-data-and-errors.md` | `DATA` | DATA-01 … DATA-24, DATA-30 … DATA-32, DATA-40 … DATA-47, DATA-50 … DATA-54, DATA-60 … DATA-62 |
| `70-testing.md` | `TEST` | TEST-01 … TEST-06, TEST-10 … TEST-16, TEST-20 … TEST-23, TEST-30 … TEST-32, TEST-40 … TEST-41, TEST-50 … TEST-52 |
| `80-ci-cd.md` | `CI` | see file (Recommended component `ci-cd`) |
| `90-ai-workflow.md` | `AI`, `SKILL` | AI-01 … AI-07, AI-20 … AI-34; SKILL-01 … SKILL-07, SKILL-10 … SKILL-17, SKILL-20 … SKILL-21, SKILL-30 … SKILL-35 |

Quick map of frequently cited rules:

| Topic | Rule |
|---|---|
| dependency direction | MOD-01 |
| feature trio | MOD-08 … MOD-12 |
| infra module pattern | MOD-13 |
| host owns environment config | MOD-15 |
| host-supplied navigation seams | MOD-18 |
| consumer-declared ports | MOD-19 |
| new-module checklist | MOD-22 |
| convention plugins (platform + feature roles) | BUILD-03 |
| module rules enforced by the build | BUILD-24 |
| Android Lint, ktlint formatting | BUILD-25, BUILD-26 |
| catalog-only versions | BUILD-10 |
| config-cache-safe reads | BUILD-14, BUILD-41 |
| versioning / signing / R8 | BUILD-17 … BUILD-22 |
| built-in Kotlin, KSP flag, Hilt minimum | BUILD-30, BUILD-32, BUILD-34 |
| qualifiers, scopes | DI-10, DI-11 |
| duplicate bindings, `@Multibinds` | DI-12, DI-13 |
| eager singletons | DI-14 |
| `Reporter` | KERNEL-01 … KERNEL-07 |
| `Scopes` and choosing a transport | KERNEL-08 … KERNEL-11 |
| main-safety, cancellation | KERNEL-15, KERNEL-16 |
| ViewModel contract | UI-01 … UI-09 |
| lifecycle-gated work | UI-13 … UI-15 |
| routes, results | UI-19, UI-21 |
| host chrome | UI-23 … UI-28 |
| permissions | UI-29 |
| token architecture | DS-01 … DS-06 |
| snackbar rules | DS-17 |
| deferred design inputs | DS-24, DS-25 |

## Decisions

| ADR | Decision |
|---|---|
| `decisions/ADR-0001-own-pager-over-paging-library.md` | own `Pager` instead of the AndroidX Paging library |
| `decisions/ADR-0002-hand-written-fakes-no-mocking-library.md` | no mocking library; hand-written fakes |
| `decisions/ADR-0003-explicit-feature-registration.md` | explicit feature registration; multibinding as the scale path |
| `decisions/ADR-0004-savedstatehandle-results-vs-scopes.md` | back-stack `SavedStateHandle` for results; `Scopes` only as a cache |
| `decisions/ADR-0005-channel-for-event-buses.md` | `Channel`, not `SharedFlow`, for handle-once event buses |
| `decisions/ADR-0006-thin-base-viewmodel-contract-interface.md` | thin `BaseViewModel` + per-screen contract interface |
| `decisions/ADR-0007-single-light-theme-default.md` | single light theme by default |
| `decisions/ADR-0008-separate-http-clients-per-concern.md` | separate qualified HTTP clients per concern |
| `decisions/ADR-0009-activity-gated-keepalive-server-decides.md` | activity-gated keep-alive; the server decides session death |
| `decisions/ADR-0010-convention-plugins-over-shared-scripts.md` | convention plugins in an included build over shared scripts |
| `decisions/ADR-0011-string-routes-over-type-safe-routes.md` | string routes kept; type-safe `@Serializable` routes rejected |
| `decisions/ADR-0012-module-graph-check-in-conventions.md` | module rules enforced by a convention task, not a third-party plugin |

## Recommended components

One file per optional component, `recommended/<component>.md`, each opening with **When to use / When not to**
and ending with **Adapting this to your app**. Written separately; rule prefixes in brackets.

- `recommended/ci-cd.md` (`CI`) — together with `80-ci-cd.md`
- `recommended/mitm-testing.md` (`MITM`, `VERIFY`)
- `recommended/network.md` (`NET`)
- `recommended/auth-session.md` (`AUTH`)
- `recommended/access-loss.md` (`ACCESS`)
- `recommended/connectivity.md` (`CONN`)
- `recommended/paging.md` (`PAGE`)
- `recommended/images.md` (`IMG`)
- `recommended/media-store.md` (`MEDIA`)
- `recommended/hardware-input.md` (`INPUT`)
- `recommended/engineering-menu.md` (`ENG`)

## Patterns

Domain-neutral recipes in `patterns/` (index: `patterns/README.md`): selection cascade, app-wide cached list,
details → item → finish, live detail, paginated picker, capture for result, configurable multi-pane, exit flow.
