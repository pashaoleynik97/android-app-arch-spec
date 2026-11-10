# Testing — the JVM kit, fakes, what to test per layer (component: architecture)

Spec: `.arch-spec/spec/specs/70-testing.md`. Golden rules in `SKILL.md`: TEST-02 (no mocking library),
TEST-51 (behaviour change ⇒ test change in the same change). Device verification is a separate, on-demand
activity (installed with the mitm-testing component).

Contents: 1. The kit · 2. Fakes · 3. What to test per layer · 4. Naming and structure · 5. Evidence habits ·
6. What runs where · 7. Shapes

---

## 1. The kit

**TEST-01** — JUnit4, kotlinx-coroutines-test (`runTest`, test dispatchers, virtual time), Turbine for flows
whose emission sequence matters, MockWebServer for anything that speaks HTTP; versions from the catalog only.
*Why:* a small, universally known kit; virtual time makes polls, TTLs, debounce and backoff testable in
milliseconds.
<!-- origin: arch-spec TEST-01 -->

**TEST-03** — Test dependencies and `isReturnDefaultValues` come from the convention plugins (BUILD-15); a
module adds only `testImplementation(project(":core-testing"))` and its own extras. *Why:* the Reporter calls
`android.util.Log`, which throws "not mocked" on the stub jar; per-module flags get forgotten.
<!-- origin: arch-spec TEST-03 -->

**TEST-04** — `:core-testing` (package `{{ROOT_PACKAGE}}.coretesting`) holds test doubles of kernel seams only:

| Type | Use |
|---|---|
| MainDispatcherRule | `@get:Rule val main = MainDispatcherRule()`; sets / resets Main; `runTest(main.dispatcher)` |
| TestDispatcherProvider | the kernel DispatcherProvider with every dispatcher = one test dispatcher |
| FakeTimeSource | manual monotonic clock: `nowMillis`, `advanceBy(millis)` |
| RecordingReporter | `@get:Rule` that captures Reporter output (`lines`, `exceptions`) |

Feature fakes (`FakeXInteractor`, `FakeXService`) live in the `src/test` of the module whose tests use them;
`:core-testing` never depends on a feature or infra module. *Why:* kernel seams are identical everywhere;
feature fakes belong to one consumer, and a shared module that knows features recompiles every test.
<!-- origin: arch-spec TEST-04 -->

**TEST-05** — Instrumented tests only where the JVM cannot reach (platform crypto, StrictMode, real OS
services); they are not part of the default gate. *Why:* emulator tests are slow and flaky; everything else
is reachable through seams.
<!-- origin: arch-spec TEST-05 -->

**TEST-06** — Compose UI tests are optional; the primary UI safety net is ViewModel tests over the Ux state
plus previews fed with literal Ux states. *Why:* asserting plain data is cheaper and more stable than
asserting a rendered tree.
<!-- origin: arch-spec TEST-06 -->

## 2. Fakes

**TEST-10** — Fakes are named `Fake<Interface>`, live next to the tests that use them (or in a
`<Area>TestFixtures.kt` with builder functions such as `item(id = "i-1")`), carry a one-line KDoc saying what
they stand in for; classes that only capture input are `Recording<X>`. *Why:* predictable names make fakes
discoverable and reusable.
<!-- origin: arch-spec TEST-10 -->

**TEST-11** — Fakes are scriptable per test: `var result: XOutcome = …`, `var onRefresh: suspend () -> …`, or a
queue of answers that fails loudly when exhausted. *Why:* each test states exactly the world it needs.
<!-- origin: arch-spec TEST-11 -->

**TEST-12** — Fakes record: call counters, argument logs, request ids seen, cancellations. *Why:* most
invariants are about what was (not) sent.
<!-- origin: arch-spec TEST-12 -->

**TEST-13** — Observed state in a fake is a `MutableStateFlow` the test drives; hot event streams expose an
`emit(…)` driver. *Why:* the test controls when upstream changes happen, so ordering bugs reproduce.
<!-- origin: arch-spec TEST-13 -->

**TEST-14** — In-flight states are tested with gates: the fake awaits a `CompletableDeferred` or holds requests
(`holdOpens = true`, `complete(id, result)`), so the test asserts the in-between state and resolves concurrent
requests out of order. *Why:* double submits, stale overwrites and races live in "while it is running".
<!-- origin: arch-spec TEST-14 -->

**TEST-15** — Navigation is asserted through a recording UiCallbacks (a list like `"details:i-1"`); exits
through `vm.exit.replayCache.firstOrNull()`. *Why:* those are the ViewModel's only navigation surfaces; no
NavController needed.
<!-- origin: arch-spec TEST-15 -->

**TEST-16** — Complex graphs (session machinery, caches with several collaborators) get a test environment
object wiring the real implementation over fakes on one `StandardTestDispatcher`, exposing the fakes for
seeding, building the subject `by lazy`, with optional background behaviour off by default. *Why:* one wiring
point per subject; tests read as scenarios, not plumbing.
<!-- origin: arch-spec TEST-16 -->

## 3. What to test per layer

**TEST-20** — Every non-trivial class has a JVM unit test:

| Layer | How | Must cover |
|---|---|---|
| pure logic (validators, mappers, policies) | plain JUnit, table-style | every branch, boundary, unknown enum, empty input |
| ViewModel | direct construction + fake interactor + `SavedStateHandle(mapOf(…))` + MainDispatcherRule | projection per outcome, every intent, navigation, resume, double-submit guards, last-tap-wins |
| interactor | fake service / store | every service rung → outcome; second-request decisions; attempt reuse |
| service | MockWebServer + real Retrofit + real converter | request path / method / headers / body; every rung incl. malformed 2xx, empty body, IO failure |
| cache / store | virtual time, fakes for session / foreground | single-flight, TTL flip, identity-change clear, pause / resume |

*Why:* each layer has a cheapest place where its contract can be pinned.
<!-- origin: arch-spec TEST-20 -->

**TEST-21** — Concurrency invariants are tested explicitly: N concurrent callers → 1 request; a superseded
late result is dropped; a cancelled caller cancels its request; a retry resends the same request id and body.
*Why:* code review misses these and production finds them.
<!-- origin: arch-spec TEST-21 -->

**TEST-22** — Endless loops (pollers, tickers) run on `backgroundScope`, and suites that start them use
`runTest(timeout = …)`. *Why:* `runTest` drains pending work; an endless poll on the test scope spins virtual
time forever.
<!-- origin: arch-spec TEST-22 -->

**TEST-23** — Time is advanced explicitly (`advanceTimeBy`, `advanceUntilIdle`, `runCurrent`); never
`Thread.sleep`, never wall-clock time. *Why:* determinism.
<!-- origin: arch-spec TEST-23 -->

## 4. Naming and structure

**TEST-30** — The class is `<ClassUnderTest>Test` (a large subject may split by behaviour); methods are
backtick sentences stating the behaviour, e.g. `` `a stale list is treated as no list` ``. *Why:* the report
reads as a specification and a failure names the broken promise.
<!-- origin: arch-spec TEST-30 -->

**TEST-31** — Large test classes group cases with `// region <topic>` … `// endregion`; assertion messages
state intent when the assertion alone does not. *Why:* navigable files, self-explaining failures.
<!-- origin: arch-spec TEST-31 -->

**TEST-32** — Tests use the production seams (fakes, test dispatchers, FakeTimeSource) — no reflection, no
`@VisibleForTesting` back doors. *Why:* tests that reach around the contract break on refactors that keep
behaviour.
<!-- origin: arch-spec TEST-32 -->

## 5. Evidence habits

**TEST-40** — Prove an invariant with a contrast pair: next to the negative case, a positive control that
differs in exactly one input. *Why:* a lone "nothing happened" also passes when the code never ran.
<!-- origin: arch-spec TEST-40 -->

**TEST-41** — Absence of a call is asserted after advancing virtual time past the window in which the call
would have happened, never immediately. *Why:* absence means something only over a window where presence was
possible.
<!-- origin: arch-spec TEST-41 -->

## 6. What runs where

**TEST-50** — The gate is `./gradlew testDebugUnitTest` (all modules, debug variant); while iterating run
`./gradlew :<module>:testDebugUnitTest`; the task plan names the exact commands. *Why:* the debug variant
covers the logic; running both variants doubles time for no signal.
<!-- origin: arch-spec TEST-50 -->

**TEST-52** — Device verification is on demand only, when the developer explicitly asks; unit tests remain
the default. *Why:* device runs are slow and stateful; they confirm integration, they do not replace tests.
<!-- origin: arch-spec TEST-52 -->

## 7. Shapes

```kotlin
class ItemListViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val interactor = FakeItemListInteractor()

    private class Callbacks : ItemListViewModelContract.UiCallbacks {
        val visited = mutableListOf<String>()
        override fun goToItem(id: String) { visited += "item:$id" }
    }

    // region opening an item
    @Test fun `a second tap on another item wins over the first`() = runTest(main.dispatcher) {
        interactor.holdOpens = true
        val callbacks = Callbacks()
        val vm = ItemListViewModel(interactor).also { it.uiCallbacks = callbacks }
        vm.onItemClick("a"); vm.onItemClick("b")
        interactor.complete("b", OpenOutcome.Ready)
        interactor.complete("a", OpenOutcome.Ready)          // late result of the superseded tap
        advanceUntilIdle()
        assertEquals(listOf("item:b"), callbacks.visited)
    }
    // endregion
}
```

The reference feature's tests (`HomeViewModelTest`, `DefaultHomeInteractorTest`) show the same shape with
the template's fake interactor.
