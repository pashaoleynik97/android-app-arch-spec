# Modules — layering, kernel, infra modules, the host, naming (component: architecture)

Spec: `.arch-spec/spec/specs/10-modules.md`. Golden rules in `SKILL.md`: MOD-01 (direction), MOD-08 (trio),
MOD-11 (no feature → feature edges), MOD-15 (one owner per environment value), MOD-22 (new-module checklist).
The feature trio itself (contract module, domain module, seams) is in the feature-trio reference.

Contents: 1. The graph · 2. Kernel modules · 3. Infra modules · 4. The host · 5. Naming · 6. Recipes

---

## 1. The graph

```
:app (host) ──▶ :x-ui ──▶ :x-ui-api ──▶ :feature-api ──▶ :lib-common
   │             │  └───▶ :x (domain) ─────────────────────▶ :lib-common
   │             └──────▶ :lib-android ──▶ :feature-api, :lib-common   (exposed with api)
   ├──▶ infra modules ──▶ :lib-common
   └──▶ every module it aggregates
:core-testing ──▶ :lib-common          (testImplementation only)
```

**MOD-02** — A module declares a dependency only on what it imports. `api(…)` is used only when a type of the
dependency appears in the module's own public signatures, and the build file comments why. *Why:* `api` leaks
compile classpaths upward; an undocumented `api` edge is the first step to a cycle.
<!-- origin: arch-spec MOD-02 -->

**MOD-03** — Core code never references an optional (Recommended) module. An optional component plugs in by
contributing to a DI set the host already consumes (`@IntoSet` + `@Multibinds`, DI-13) or through an installer
marker insert in the host. *Why:* every component must build on its own; a Core → optional edge makes the
optional module mandatory.
<!-- origin: arch-spec MOD-03 -->

How an optional module plugs into the host — the two seams in `:lib-android` (package
`{{ROOT_PACKAGE}}.libandroid.host`), both declared empty-safe with `@Multibinds` in the host's
`HostExtensionsModule`:

| Seam | Use for | Host side |
|---|---|---|
| HostExtension (`order`, lower = outer; `@Composable Wrap(navController, content)`) | host banners, navigation on session end, global notices | `MainActivity` injects the set; `AppNavHost` nests them around the screen-banner host, inside the chrome CompositionLocals |
| HostActivityObserver (a DefaultLifecycleObserver) | work bound to the single Activity's lifecycle (claiming a device while in front) | `MainActivity` registers each on its lifecycle |

```kotlin
// in the OPTIONAL module's own Hilt module — Core code never names it
@Binds @IntoSet abstract fun bindBanner(impl: ConnectivityBannerExtension): HostExtension
```

A `Wrap` calls `content` exactly once; leave gaps in `order` (10, 20, …). An optional **UI** feature is
registered through the host's `feature-registrations` marker (an installer insert), not through these seams.

## 2. Kernel modules

| Module | Holds | Must not hold |
|---|---|---|
| `:lib-common` | Reporter, Scopes, AppForegroundWatch, DispatcherProvider / TimeSource seams, small pure utils, KernelModule | Compose, UI, navigation |
| `:feature-api` | FeatureApi, FeaturesHolder, navigation types used by that contract (exposed with `api`) | Hilt, Compose compiler, screens |
| `:lib-android` | theme + tokens, UI kit, host-chrome controllers and `Set…` effects, BaseViewModel / ViewModelState, AppTransitions, nav-result helpers, preview annotations, host extension seams | any infra module or feature contract |
| `:core-testing` | MainDispatcherRule, TestDispatcherProvider, FakeTimeSource, RecordingReporter | anything feature- or infra-specific |

**MOD-04** — `:lib-common` is the kernel: no Compose, no UI, no navigation. It may use the Android framework
(`Log` inside the Reporter, the process lifecycle, `BuildConfig`) and Hilt; it is not "framework-agnostic", and
a project that needs a pure-JVM layer splits one out deliberately. *Why:* calling it framework-agnostic invites
an agent to "fix" it by stripping working code; the honest boundary is "no UI".
<!-- origin: arch-spec MOD-04 -->

**MOD-05** — `:feature-api` holds only the feature contract and registry and the navigation/Compose types that
contract exposes. No Hilt, no Compose compiler, no screens. *Why:* every contract module depends on it;
anything heavier is compiled into all of them.
<!-- origin: arch-spec MOD-05 -->

**MOD-06** — `:lib-android` is the Android/Compose toolkit; it exposes `:lib-common` and `:feature-api` with
`api` and never depends on an infra module or a feature contract. *Why:* a toolkit that sees infra couples every
screen to it; mappers between infra and UI belong to the host (MOD-16).
<!-- origin: arch-spec MOD-06 -->

**MOD-07** — `:core-testing` holds test doubles of kernel seams only, is consumed with `testImplementation`
only, and never appears in a main or debug configuration. *Why:* duplicated per-module fakes drift; a test
module on a runtime classpath ships test code.
<!-- origin: arch-spec MOD-07 -->

## 3. Infra modules

**MOD-13** — An infrastructure service (HTTP stack, session, connectivity, files, device storage, hardware
input) is a standalone single module with no UI. It owns mechanics only; exposes interfaces with `internal`
implementations bound in its own Hilt module; owns its own qualified client, `CoroutineScope` and dispatcher
(DI-10, DI-11); declares a no-default config class (MOD-15); and is consumed by domain modules and the host,
never by a kernel module. *Why:* isolated infra with its own clients stops one concern (token refresh) from
leaking into another (a health probe).
<!-- origin: arch-spec MOD-13 -->

## 4. The host

**MOD-14** — `:app` is the composition root and the only module that sees every other one. It owns the Hilt
Application (`App`), the single Activity (`MainActivity`), the single NavHost (`AppNavHost`), feature
registration, host chrome, the host shell ViewModel when one exists, and DI modules for environment config and
cross-sibling bindings. Every dependency line in `app/build.gradle.kts` carries a short comment saying why the
host needs it. *Why:* host-level outcomes (session end, access loss) are handled once above every feature; the
comments stop the dependency list from becoming a dumping ground.
<!-- origin: arch-spec MOD-14 -->

**MOD-16** — Code that needs two sibling modules at once (a mapper from an infra state to a kit model, an
adapter from one feature's port to another feature's API) lives in `:app`. *Why:* the host is the only module
allowed to see both sides; anywhere else it creates the edge MOD-06 / MOD-11 forbid.
<!-- origin: arch-spec MOD-16 -->

**MOD-17** — A fake or diagnostic implementation that must never ship lives in its own module wired with
`debugImplementation` and discovered through DI; release builds must build without it. *Why:* a debug source
set inside a shared module is easy to reference from main code by accident; a separate module makes the
release build prove the separation.
<!-- origin: arch-spec MOD-17 -->

## 5. Naming

**MOD-20** — Names and packages:

| Kind | Module | Package (= Android `namespace`) |
|---|---|---|
| host | `:app` | `{{APP_PACKAGE}}` |
| kernel | `:lib-common`, `:lib-android`, `:feature-api`, `:core-testing` | `{{ROOT_PACKAGE}}.libcommon`, `.libandroid`, `.featureapi`, `.coretesting` |
| feature trio | `x-ui-api`, `x-ui`, `x` (kebab-case) | `{{ROOT_PACKAGE}}.<x>.ui.api`, `.ui`, `.domain` (`<x>` without dashes) |
| infra | network, auth, connectivity, … | `{{ROOT_PACKAGE}}.<name>` |
| contract-only leaf | `y-api` | `{{ROOT_PACKAGE}}.y.api` |

Routes are `"<x>/<screen>"`; result keys `"<x>.result.<name>"`. *Why:* package = namespace = module name makes
every class's owning module obvious from its import line.
<!-- origin: arch-spec MOD-20 -->

**MOD-21** — `settings.gradle.kts` has one `include(…)` per line, grouped by family (kernel, infra, each
feature trio) under comment headers; installer-managed lines stay inside the `arch-spec:includes` marker
block. *Why:* grouped includes are reviewable; the marker block lets components be installed idempotently.
<!-- origin: arch-spec MOD-21 -->

## 6. Recipes

**Add an infra module** (no installer component for it): create the module per MOD-22 with the library +
hilt conventions; put interfaces at the package root, implementations `internal`; `…/di/Qualifiers.kt` and
`…/di/<Name>Module.kt`; a `data class <Name>Config` with no defaults; in `:app`, provide the config from the
host's environment DI module and add a commented `implementation(project(":<name>"))`; add the module line to
`CLAUDE.md` and, if it carries rules, a reference file here (via skills-maintenance, capture).

**Check the direction** before adding a dependency: find the two modules in the graph above; the edge must
point down. If it points up or sideways, use a host seam (feature-trio reference) or move the code to the host
(MOD-16).
