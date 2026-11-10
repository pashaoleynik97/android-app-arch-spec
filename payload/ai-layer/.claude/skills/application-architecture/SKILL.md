---
name: application-architecture
description: >
  Architecture rules for this Android project — module layout and dependency direction, the build-logic
  convention-plugin build and version catalog, toolchain gotchas, Hilt DI, the kernel (logging through the
  Reporter, flow-scoped state, dispatcher and clock seams), the ViewModel and navigation contract, domain
  outcomes and errors, unit testing with hand-written fakes, and every installed infra component. Use this
  whenever adding or changing a module or feature, wiring DI, editing Gradle files or the catalog, writing
  domain, ViewModel or test code, or navigating the codebase so new code matches existing conventions.
---

# Application architecture

The project's architecture rules: how modules are cut and depend on each other, how the build is put
together, and how code is written inside each layer. This file holds the rules that apply to almost every
task; the references below hold the detail for one area each. Rules carry IDs from the architecture spec
(`.arch-spec/spec/specs/`, normative text with full reasoning) or `PRJ-` IDs for rules this project added.

## How to use this skill

1. Read the golden rules below — they apply to every change.
2. Open the one reference for the area you touch (index at the end). Do not load all of them.
3. Copy the shape of existing code before inventing one: the reference feature is `home`
   (modules `home-ui-api`, `home-ui`, `home`), the host is `app`.
4. If the task changes a convention, update the rule here or in the reference in the same change
   (see "Changing this skill").

Where things live:

| Looking for | Place |
|---|---|
| versions, plugin aliases | `gradle/libs.versions.toml` |
| shared build config, SDK levels | `build-logic/` (convention plugins, ProjectConfig) |
| logging, flow state, seams | `lib-common` (package `{{ROOT_PACKAGE}}.libcommon`) |
| base ViewModel, theme, UI kit, host chrome, nav helpers | `lib-android` (package `{{ROOT_PACKAGE}}.libandroid`) |
| feature contract + registry | `feature-api` (package `{{ROOT_PACKAGE}}.featureapi`) |
| shared test doubles for kernel seams | `core-testing` (package `{{ROOT_PACKAGE}}.coretesting`) |
| Application, Activity, NavHost, environment config, cross-feature wiring | `app` (package `{{APP_PACKAGE}}`) |
| installer state, deferred items, pristine baseline | `.arch-spec/state.json`, `.arch-spec/baseline/` |

## Golden rules

### Structure

**MOD-01** — Dependencies point one way — host → features / infra → kernel (`lib-android` → `feature-api` →
`lib-common`) — and are never inverted: the kernel never depends on a feature or infra module, a domain module
never depends on a UI module, nothing depends on the host. *Why:* an inverted edge turns the kernel into a hub
that recompiles with every feature and makes features impossible to build or test alone.
<!-- origin: arch-spec MOD-01 -->

**MOD-08** — Every feature with UI is three modules: `x-ui-api` (marker FeatureApi + routes, depends only on
`feature-api`), `x-ui` (FeatureImpl, screens, ViewModels; depends on `lib-android`, `x-ui-api`, `x`) and `x`
(domain: interactors, models, services, its own Hilt module; depends on `lib-common` + the infra it uses). A
trivial feature may omit `x`. Create one with the **add-feature** skill. *Why:* the host and siblings navigate
to a feature through its tiny contract without compiling its UI or domain; editing a screen recompiles only
`x-ui` and the host.
<!-- origin: arch-spec MOD-08 -->

**MOD-11** — A feature's modules never depend on another feature's modules. Cross-feature navigation goes
through host-supplied seams (nullable constructor parameters of the FeatureImpl, filled by the host); a domain
that needs a sibling declares its own port, bound by the host. *Why:* without edges between features any
feature builds and runs alone, and the graph stays a star around the host instead of a mesh.
<!-- origin: arch-spec MOD-11 -->

**MOD-15** — Every environment value (base URLs, hosts, keys) is set in exactly one place: infra modules declare
a config data class with no defaults; the value comes from `:app`, or from an optional component's own build
file (per-build-type `BuildConfig`). Never a Kotlin default, never a second copy.
*Why:* a default URL inside a library silently points a release build at the wrong environment; a missing
binding fails the build instead.
<!-- origin: arch-spec MOD-15 -->

**MOD-22** — A new module ships, in the same change: a declarative `build.gradle.kts` using
`{{CONVENTION_PREFIX}}.android.*` conventions and `namespace` = package; `<module>/src/main/AndroidManifest.xml`; an
`include(...)` line in `settings.gradle.kts`; a `.gitignore` with `/build`; the hilt convention if it declares
any injected type; a line in the module list of `CLAUDE.md`; a commented dependency line in the host if the
host consumes it; unit tests for every non-trivial class. *Why:* each missing item fails late and confusingly
(build output committed, KSP silently not running, an agent not knowing the module exists).
<!-- origin: arch-spec MOD-22 -->

### Build

**BUILD-10** — Every external version is declared in `gradle/libs.versions.toml` and referenced as `libs.*`;
no inline version in any build file (the one tolerated exception, a settings-level plugin, carries a comment).
*Why:* one place to upgrade and audit; inline versions are how two copies of a library end up on the
classpath.
<!-- origin: arch-spec BUILD-10 -->

**BUILD-30** — On AGP with built-in Kotlin, never apply (or declare) the standalone Kotlin Android plugin;
compiler options use `kotlin { compilerOptions { } }` inside a convention. *Why:* the standalone plugin on top
of built-in Kotlin fails configuration or registers sources twice.
<!-- origin: arch-spec BUILD-30 -->

### Code

**DI-06** — A module's public surface is an interface; the implementation is `internal` and bound with
`@Binds` in that module's own Hilt module (`di/<Name>Module.kt`, installed in the singleton component).
Constructor injection everywhere else. *Why:* consumers depend on something they can fake, and `@Binds` costs
no generated factory.
<!-- origin: arch-spec DI-06 -->

**KERNEL-01** — All logging goes through the kernel Reporter
(`Reporter.debug("…", where = logTag)` with `private val logTag = logTag()`); `android.util.Log` appears only
inside the Reporter, never `println`; credentials, tokens and personal identifiers are never logged.
*Why:* one seam is where debug gating, masking and crash-reporter fan-out happen; one bypass leaks a secret or
loses a crash.
<!-- origin: arch-spec KERNEL-01 -->

**KERNEL-09** — Choose the transport by what must survive process death: ids → route arguments; a result for
the previous screen → that back-stack entry's saved state; a large read model shared by one flow → the
in-memory Scopes holder (a cache only); anything persistent → a store in a domain or infra module. *Why:* the
in-memory holder is gone after the system kills a backgrounded app; a flow that relies on it alone shows empty
screens on restore.
<!-- origin: arch-spec KERNEL-09 -->

**KERNEL-15** — Every `suspend` function of a domain or infra API is main-safe: it switches to its injected
dispatcher for blocking work; callers never wrap calls in `withContext`. *Why:* ViewModels call from the main
thread; if safety depends on every caller remembering, one forgets.
<!-- origin: arch-spec KERNEL-15 -->

**KERNEL-16** — Cancellation always propagates: catch only the specific exceptions a layer can classify
(IOException, SerializationException), never a blanket `catch (e: Exception)` or `runCatching` around a
suspend call. *Why:* swallowed cancellation turns "last tap wins" and screen exit into zombie work that writes
stale state.
<!-- origin: arch-spec KERNEL-16 -->

**UI-01** — Every screen has one ViewModel:
`@HiltViewModel class XViewModel @Inject constructor(…) : BaseViewModel(), XViewModelContract`, with a
private State, a single `toUxState()` projection exposed as a `StateFlow`, and intents as `onX()` methods.
*Why:* one shape for every screen lets any screen be read or written by analogy, and tests target the
contract the screen actually uses.
<!-- origin: arch-spec UI-01 -->

**UI-06** — ViewModels are Context-free: no Context, Resources, string lookups, NavController or Compose
types; Ux types hold enums, ids and plain data and the composable maps them to resources. *Why:* Context-free
ViewModels run in plain JVM tests and never leak an Activity.
<!-- origin: arch-spec UI-06 -->

**UI-18** — There is one NavHost, in the host; features attach destinations through their FeatureImpl
registered at startup, and all NavController code lives in that FeatureImpl — screens get lambdas, ViewModels
get a `uiCallbacks` seam. *Why:* navigation is wiring; one file per feature keeps flows readable and screens
reusable and testable.
<!-- origin: arch-spec UI-18 -->

**DATA-03** — Interactors return feature-level sealed outcomes; they never throw for expected failures and
never hand `kotlin.Result`, HTTP responses, status codes, DTOs or exceptions to a ViewModel. *Why:* the
ViewModel's state machine must be exhaustive over what the user can experience, checked by the compiler.
<!-- origin: arch-spec DATA-03 -->

### Quality

**TEST-02** — No mocking library. Test doubles are hand-written fakes implementing the production interface,
scriptable per test and recording what they received. *Why:* a fake documents the contract, is reused across
tests and never breaks on final, suspend or inline types.
<!-- origin: arch-spec TEST-02 -->

**TEST-51** — A change that adds or alters behaviour adds or updates the JVM unit test that pins it in the
same change, and the task log records the command and counts. *Why:* an untested behaviour change is
indistinguishable from a regression.
<!-- origin: arch-spec TEST-51 -->

## Verify

```
./gradlew :<module>:testDebugUnitTest      # while iterating
./gradlew assembleDebug testDebugUnitTest  # before declaring done
```

## References

One file per area; each states its rules with IDs, the reason, and short code shapes. Components add their
own line when installed.

<!-- <arch-spec:references> -->
<!-- </arch-spec:references> -->

## Changing this skill

Rules change only through the **skills-maintenance** skill: *amend* for an existing rule (spec rules get a
Project override block, never a silent edit), *capture* for a new convention (a `PRJ-` rule with origin
`project`), and a `CHANGELOG.md` entry next to this file either way.
