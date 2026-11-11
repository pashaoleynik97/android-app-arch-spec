# 10 — Modules: layering, feature trio, infra modules, host seams (MOD)

Tier: **Core** (component `architecture`). Rules use RFC 2119 wording. Placeholders: `{{ROOT_PACKAGE}}`,
`{{APP_PACKAGE}}`, `{{CONVENTION_PREFIX}}` (see `installer/parameters.md`).

Contents: 1. Layering · 2. Kernel modules · 3. Feature trio · 4. Infra modules · 5. The host · 6. Seams between
siblings · 7. Naming and packages · 8. New-module checklist

---

## 1. Layering and dependency direction

```
:app (host) ──▶ :x-ui ──▶ :x-ui-api ──▶ :feature-api ──▶ :lib-common
   │             │  └───▶ :x (domain) ─────────────────────▶ :lib-common
   │             └──────▶ :lib-android ──▶ :feature-api
   │                                  └──▶ :lib-common
   ├──▶ infra modules (:network, :auth, …) ──▶ :lib-common
   └──▶ every module it aggregates
:core-testing ──▶ :lib-common          (testImplementation only)
```

**MOD-01** — Dependencies MUST point downward along the graph above and MUST NEVER be inverted: kernel modules
(`:lib-common`, `:feature-api`, `:lib-android`) never depend on a feature or infra module; domain modules never
depend on UI modules; nothing depends on `:app`.
The build enforces this rule, MOD-07, MOD-09, MOD-10, MOD-11 and MOD-19 with `checkModuleGraph` (BUILD-24).
*Why:* an inverted edge turns the kernel into a hub that recompiles with every feature and makes features
impossible to build or test alone.

**MOD-02** — A module MUST declare a dependency only on what it imports. Transitive exposure (`api(...)`) is
used only when a type of the dependency appears in the module's own public signatures, and the build file
comments why.
*Why:* `api` leaks compile classpaths upward; an undocumented `api` edge is the first step to a cycle.

**MOD-03** — Core code MUST NOT reference an optional (Recommended) module. An optional component integrates by
contributing into a DI set the host already consumes (`@IntoSet` + `@Multibinds`, see DI-13) or through an
installer marker insert in the host — never by a Core file importing it.
*Why:* every component must build on its own on top of its declared dependencies; a Core → optional edge makes
the optional module mandatory.

## 2. Kernel modules

**MOD-04** — `:lib-common` is the kernel: **no Compose, no UI, no navigation**. It MAY use the Android framework
(`Log` inside `Reporter`, `SharedPreferences`, `ProcessLifecycleOwner`, `BuildConfig`) and Hilt. It holds
`Reporter`, `Scopes`, `AppForegroundWatch`, the `DispatcherProvider` / `TimeSource` seams and small pure utils
(see `40-kernel.md`). It is **not** described as "framework-agnostic"; a project that needs a pure-JVM layer
splits a separate JVM module out of it deliberately.
*Why:* naming a module "framework-agnostic" while it uses `android.*` invites agents to "fix" it by stripping
working code; the honest boundary is "no UI".

**MOD-05** — `:feature-api` holds only the feature contract and registry (`FeatureApi`, `FeaturesHolder`) and
the navigation/Compose types that appear in that contract (exposed with `api`). No Hilt, no Compose compiler
plugin, no screens.
*Why:* every `:x-ui-api` depends on it; anything heavier here is compiled into every contract module.

**MOD-06** — `:lib-android` is the Android/Compose toolkit: theme and tokens, base UI kit, `BaseViewModel` /
`ViewModelState`, host-chrome controllers, navigation helpers (`AppTransitions`). It exposes `:lib-common` and
`:feature-api` with `api`. It MUST NOT depend on any infra module or feature contract.
*Why:* a UI toolkit that sees infra (e.g. connectivity types) couples every screen to it; mappers between infra
and UI belong to the host (MOD-16).

**MOD-07** — `:core-testing` holds shared test fixtures for kernel seams (fake `DispatcherProvider`,
fake `TimeSource`, recording `Reporter.ExternalReporter`, main-dispatcher helpers). It is consumed with
`testImplementation` only and MUST NEVER appear in a main or debug configuration. Detail lives in
`70-testing.md`.
*Why:* duplicated per-module fakes drift; a test module on a runtime classpath ships test code.

## 3. The feature trio

**MOD-08** — Every feature `x` with UI MUST be three modules:

| Module | Package | Holds | Depends on |
|---|---|---|---|
| `:x-ui-api` | `{{ROOT_PACKAGE}}.x.ui.api` | marker `XFeatureApi : FeatureApi`, `object XRoutes`, small link/data types | `api(:feature-api)` only |
| `:x-ui` | `{{ROOT_PACKAGE}}.x.ui` | `XFeatureImpl`, screens, ViewModels, contracts, strings, drawables | `:lib-android`, `:x-ui-api`, `:x` |
| `:x` | `{{ROOT_PACKAGE}}.x.domain` | interactors, models, services, DTOs, stores, its DI module | `:lib-common` (+ infra it uses) |

A trivial feature with no domain logic MAY omit `:x`.
*Why:* the host and sibling features need to navigate to `x` (routes, result keys) without compiling its UI or
domain; the contract module is tiny and stable, so editing a screen recompiles only `:x-ui` and `:app`.

**MOD-09** — `:x-ui-api` is an Android library (route builders use `android.net.Uri.encode`) built with the
`{{CONVENTION_PREFIX}}.feature.api` convention only — the library convention plus exactly one dependency,
`api(project(":feature-api"))`; no Compose convention, no Hilt. `:x-ui` uses `{{CONVENTION_PREFIX}}.feature.ui`
and `:x` uses `{{CONVENTION_PREFIX}}.feature.domain` (BUILD-03).
*Why:* a contract that pulls Compose or Hilt makes every consumer pay for it.

**MOD-10** — `:x` (domain) MUST NOT use Compose, `ViewModel`, lifecycle, or navigation types. It exposes
interfaces (interactor, service, store) that `:x-ui` fakes in tests, and returns feature-level sealed outcomes,
never HTTP types (see `60-data-and-errors.md`). Its Hilt module lives in `:x` (`domain/di/XDomainModule.kt`).
*Why:* the ViewModel branches on outcomes only; keeping HTTP and persistence out of `:x-ui` keeps VM tests pure.

**MOD-11** — A `:x-ui` module MUST NOT depend on another feature's `:y-ui`, `:y`, or `:y-ui-api`. Navigation to
a sibling goes through host-supplied seams (MOD-18). `checkModuleGraph` fails the build on such an edge
(BUILD-24).
*Why:* with no edges between features, any feature builds and runs standalone, and the dependency graph stays a
star around the host instead of a mesh.

**MOD-12** — Package layout inside a trio:

```
:x-ui-api  …/x/ui/api/XFeatureApi.kt, XRoutes.kt, [XLink.kt]
:x-ui      …/x/ui/XFeatureImpl.kt
           …/x/ui/<screen>/XScreen.kt, XViewModel.kt, XViewModelContract.kt, components/*.kt
           …/x/ui/components/*.kt                      (shared within the feature)
:x         …/x/domain/XInteractor.kt                   (interface + Default impl)
           …/x/domain/model/XModels.kt
           …/x/domain/network/{XApi, XService, RetrofitXService, XMapper}.kt, dto/XDtos.kt
           …/x/domain/di/XDomainModule.kt
```
*Why:* one predictable place per role lets an agent find and extend code without searching; the feature
template (`payload/architecture/feature-template/`) renders exactly this shape.

## 4. Infra modules

**MOD-13** — An infrastructure service (HTTP stack, session, connectivity, files, device storage, hardware input)
MUST be a standalone single module (`:network`, `:auth`, …) with **no UI**. It:
- owns mechanics only — clients, interceptors, stores, schedulers;
- exposes public interfaces with `internal` implementations bound in its own Hilt module (DI-06);
- owns its own qualified OkHttp client, `CoroutineScope` and dispatcher (DI-10, DI-11), never borrowing another
  module's unqualified one;
- declares a **no-default config class** for environment values (MOD-15);
- is consumed by domain modules and the host, never by `:lib-common`, `:lib-android` or `:feature-api`.
*Why:* infra changes rarely and is shared by many features; isolating it with its own clients prevents one
concern (e.g. token refresh) from leaking into another (e.g. a health probe).

## 5. The host (`:app`)

**MOD-14** — `:app` is the composition root and the only module allowed to see every other module. It owns:
`@HiltAndroidApp` Application, the single `@AndroidEntryPoint` Activity, the single `NavHost`, feature
registration, host chrome, the host shell ViewModel (session end, global notices, connectivity banner), and the
DI modules for environment config and cross-sibling bindings. Every dependency line in `app/build.gradle.kts`
MUST carry a short comment saying why the host needs it.
*Why:* host-level outcomes (session death, access loss) must be handled once, above every feature; the comments
stop the dependency list from becoming a dumping ground.

**MOD-15** — **Environment configuration has exactly one owner.** Infra modules declare a config class with no
default values (`NetworkConfig(baseUrl)`, `ConnectivityConfig(baseUrl, healthPath)`). Each environment value is
set in **exactly one place**: `:app`, or, for an optional component that Core must not know, that component's
own build file (per-build-type `BuildConfig` fields rendered from the installer answers). No Kotlin default, and
no second copy anywhere. Tuning values that are not environment (timeouts, lead times)
MAY be defaulted by the owning module; values the host must shrink in tests are injected configs too.
*Why:* a default URL inside a library silently points a release build at the wrong environment; a missing
binding instead fails the build.

**MOD-16** — Code that needs two sibling modules at once (a mapper from an infra state to a UI-kit model, an
adapter from one feature's port to another feature's API) MUST live in `:app`.
*Why:* `:app` is the only module that may see both sides; putting the mapper in either side creates the very
edge MOD-06/MOD-11 forbid.

**MOD-17** — A fake or diagnostic implementation that must never ship (an adb-driven input driver, a debug menu
backend) lives in its own module wired with `debugImplementation`, discovered by the host through DI.
Release variants MUST build without it.
*Why:* a debug-only source set inside a shared module is easy to reference from main code by accident; a
separate module makes the release build prove the separation.

## 6. Seams between siblings

**MOD-18** — Cross-feature navigation uses **host-supplied seams**: `XFeatureImpl` declares nullable
constructor parameters with `null` defaults, and `:app` fills them when it registers features.

| Seam | Type | Example |
|---|---|---|
| fixed route | `String?` | `homeRoute: String? = null` |
| route builder | `((id: String) -> String)?` | `detailsRoute = DetailsRoutes::details` |
| result key the sibling writes | `String?` | `itemGoneResultKey = ListRoutes.RESULT_ITEM_GONE` |
| route + key bundle | small data class in the **caller's** `:x-ui-api` | `data class CaptureLink(val route: (String) -> String, val resultKey: String)` |

```kotlin
// :app — App.registerFeatures()
ListFeatureImpl(detailsRoute = DetailsRoutes::details).addToRegistry()
DetailsFeatureImpl(itemGoneResultKey = ListRoutes.RESULT_ITEM_GONE).addToRegistry()
```
A `null` seam MUST leave the action inert (or route to a local stub destination), never crash. Route and result
key that form one contract travel together in one link type.
*Why:* a compile dependency between two `:x-ui` modules would invert the module rules; nullable seams keep each
feature runnable alone and keep the caller ignorant of the callee's vocabulary.

**MOD-19** — Sibling **domain** modules never depend on each other. When `:a` must trigger something in `:b`,
`:a` declares its **own** tiny port (`fun interface ListInvalidator { suspend fun invalidate() }`), leaves it
unbound in its DI module (and says so in the module KDoc), and `:app` binds it to `:b`'s API in a host DI module.
Two features needing the same capability declare two ports.
*Why:* a shared port would have to live somewhere both can see — exactly the dependency the rules forbid; a
per-consumer port also documents what each feature actually needs.

## 7. Naming and packages

**MOD-20** — Names:

| Kind | Module | Package |
|---|---|---|
| host | `:app` | `{{APP_PACKAGE}}` |
| kernel | `:lib-common`, `:lib-android`, `:feature-api`, `:core-testing` | `{{ROOT_PACKAGE}}.libcommon`, `.libandroid`, `.featureapi`, `.coretesting` |
| feature trio | `:x-ui-api`, `:x-ui`, `:x` (kebab-case `x`) | `{{ROOT_PACKAGE}}.<x>.ui.api`, `.ui`, `.domain` (`<x>` lowercase, no dashes) |
| infra | `:network`, `:auth`, `:connectivity`, … | `{{ROOT_PACKAGE}}.network`, … |
| contract-only leaf | `:y-api` | `{{ROOT_PACKAGE}}.y.api` |

The Android `namespace` equals the package. Route strings use `"<x>/<screen>"`; result keys use
`"<x>.result.<name>"`.
*Why:* package == namespace == module name makes every class's owning module obvious from its import line.

**MOD-21** — `settings.gradle.kts` lists one `include(...)` per line, grouped by family (kernel, infra, each
feature trio) under a comment header; installer-managed lines stay inside the
`// <arch-spec:includes>` … `// </arch-spec:includes>` marker block.
*Why:* grouped includes are reviewable; the marker block lets components be installed and re-installed
idempotently.

## 8. New-module checklist

**MOD-22** — Every new module MUST have, in the same change:
1. `build.gradle.kts` applying conventions only — the `{{CONVENTION_PREFIX}}.feature.*` role convention for a
   feature-trio module, `{{CONVENTION_PREFIX}}.android.*` otherwise (no inline Android config or baseline edge that
   a convention owns, see `20-build-system.md`), `namespace` per MOD-20;
2. `src/main/AndroidManifest.xml` (an empty `<manifest/>` for libraries);
3. an `include(...)` in `settings.gradle.kts` (MOD-21);
4. a `.gitignore` containing `/build`;
5. the `{{CONVENTION_PREFIX}}.android.hilt` convention if it declares any `@Inject` / `@Module` type (DI-05;
   `feature.ui` and `feature.domain` include it);
6. an entry in the project's module table (CLAUDE.md / architecture skill) and, if the host consumes it, a
   commented dependency line in `app/build.gradle.kts`;
7. unit tests for every non-trivial class (`70-testing.md`).
*Why:* each missing item fails late and confusingly (build output committed, KSP silently not running, an agent
not knowing the module exists).
