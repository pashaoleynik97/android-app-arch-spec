# 70 — Testing (TEST-*)

Tier: **Core** (MUST). Scope: the JVM unit-test kit, the `:core-testing` module, fake conventions, what is tested
per layer, naming, and what CI runs. Device-level verification against scripted server responses is Recommended
and specified in `specs/recommended/mitm-testing.md`.

The design goal: **every behaviour that matters is pinned by a fast, deterministic JVM test**, because every
non-JVM dependency sits behind a seam (`specs/60-data-and-errors.md` DATA-06/07) and every coroutine runs on a
dispatcher the test owns.

---

## 1. The kit

**TEST-01** — Unit tests use **JUnit4**, **kotlinx-coroutines-test** (`runTest`, `StandardTestDispatcher`,
`UnconfinedTestDispatcher`, virtual time), **Turbine** (for flows whose emission *sequence* matters) and OkHttp
**MockWebServer** (for anything that speaks HTTP). Versions come from the version catalog only.
*Why:* a small, boring, universally known kit; virtual time makes timing behaviour (polls, TTLs, debounce,
backoff) testable in milliseconds.

**TEST-02** — **No mocking library** (no MockK, no Mockito). Test doubles are hand-written fakes implementing the
production interface.
*Why:* a fake is a second implementation of the contract — it documents behaviour, is reused across tests, never
breaks on final/value/suspend/inline types, and failures read as behaviour instead of "unexpected call".

**TEST-03** — Test dependencies and test options are applied by the **convention plugins** (`specs/20-build-system.md`),
not repeated per module: the standard test libraries as `testImplementation`, and
`testOptions.unitTests.isReturnDefaultValues = true`.
*Why:* the logging façade (`Reporter`) calls `android.util.Log`, which throws "not mocked" on the stub
`android.jar`; a per-module flag gets forgotten in the next module.

**TEST-04** — The `:core-testing` module (package `{{ROOT_PACKAGE}}.coretesting`) holds the test-side doubles of
**kernel seams only**, and is consumed as `testImplementation(project(":core-testing"))`:

| Type | Purpose |
|---|---|
| `MainDispatcherRule(dispatcher: TestDispatcher = UnconfinedTestDispatcher())` | JUnit `TestWatcher` that calls `Dispatchers.setMain` before and `resetMain` after each test; exposes `dispatcher` / `scheduler` |
| `TestDispatcherProvider(dispatcher)` | the kernel's `DispatcherProvider` with every dispatcher = one test dispatcher |
| `FakeTimeSource(scheduler)` | the kernel's `TimeSource`, reading `scheduler.currentTime` so `delay` and clock reads agree; a manual variant with `advanceBy(...)` for non-coroutine code |
| recording `Reporter.ExternalReporter` | captures log calls so a test can assert that something was (not) reported (`specs/40-kernel.md`) |

Feature fakes (`FakeXInteractor`, `FakeXService`) do **not** go into `:core-testing`; they live in the `src/test`
of the module whose tests use them. `:core-testing` MUST NOT depend on any feature or infra module.
*Why:* kernel seams are needed everywhere and identical everywhere; feature fakes belong to one consumer, and a
shared module that knows features becomes a dependency hub that recompiles every test on every change.

**TEST-05** — Instrumented tests (`src/androidTest`) are used **only** where the JVM cannot reach: real platform
crypto (Keystore), StrictMode main-thread checks, real OS services. They are not part of the default CI gate.
*Why:* emulator tests are slow and flaky in CI; everything else is reachable through seams.

**TEST-06** — Compose UI tests are optional (MAY). The primary UI safety net is: ViewModel tests over `UxState`
+ previews fed with literal `UxState` values (`specs/50-presentation.md`).
*Why:* `UxState` is plain data — asserting it is cheaper and more stable than asserting a rendered tree.

---

## 2. Fake conventions

**TEST-10** — Fakes are named `Fake<Interface>` and live in `src/test/.../<feature>/<layer>/Fake<Interface>.kt` or
in a `<Area>TestFixtures.kt` file together with builder functions (`item(id = "i-1", …)` with default
arguments). Classes that only capture what they are given are named `Recording<X>`. Each fake has a one-line
KDoc saying what it stands in for.
*Why:* predictable names make fakes discoverable and reusable.

**TEST-11** — Fakes are **scriptable**: per-call outcomes are replaceable per test —
`var result: XOutcome = XOutcome.Success(...)`, `var onRefresh: suspend () -> Outcome`, or an
`ArrayDeque<XFetchResult>` of queued answers (one per call, failing loudly when exhausted).
*Why:* each test states exactly the world it needs, in its own body.

**TEST-12** — Fakes **record**: call counters (`refreshCalls`), argument logs (`requests: List<…>`, `policies`,
`requestIdsSeen`), and cancellations (`cancelledOpens`).
*Why:* most invariants are about what was (not) sent — counts, ids reused, policies used.

**TEST-13** — Observed state in a fake is a `MutableStateFlow` the test drives (`val snapshot =
MutableStateFlow(Snapshot())`); hot event streams expose an `emit(...)` / `send(...)` driver.
*Why:* the test controls *when* upstream changes happen, so ordering bugs become reproducible.

**TEST-14** — **In-flight states** are tested with gates: the fake awaits a `CompletableDeferred` (`gate`) or
holds requests (`holdOpens = true`, `complete(id, result)`) so the test can assert the in-between state (spinner,
disabled button, second tap ignored) and resolve concurrent requests **out of order**.
*Why:* "what happens while it is running" is where double submits, stale overwrites and races live.

**TEST-15** — Navigation is asserted through a **recording `UiCallbacks`** implementation (a list of visited
destinations as strings, e.g. `"details:i-1"`); one-shot exits through `vm.exit.replayCache.firstOrNull()`.
*Why:* the VM's only navigation surface is `UiCallbacks` / the exit flow; recording them needs no NavController.

**TEST-16** — For complex graphs (session machinery, caches with several collaborators) a **test environment
object** (`XTestEnv(testScope)`) wires the real implementation over fakes on one `StandardTestDispatcher`,
exposes the fakes for seeding, builds the subject `by lazy` (seed first — constructors launch coroutines in
`init`), and keeps optional background behaviour **off** by default, opted in per test.
*Why:* one wiring point per subject; tests read as scenario setup, not plumbing.

---

## 3. What is tested, per layer

**TEST-20** — Every non-trivial class has a JVM unit test. By layer:

| Layer | How | Must cover |
|---|---|---|
| Pure logic (validators, mappers, policies, resolvers, formatters) | plain JUnit, table-style | **exhaustively**: every branch, every boundary, unknown enum values, empty inputs |
| ViewModel | constructed directly with fake interactor + `SavedStateHandle(mapOf(...))`, `MainDispatcherRule` | state projection for each outcome, every intent, navigation via recording callbacks, resume semantics, double-submit guards, concurrency (last-tap-wins, cancellation) |
| Interactor | fake service/store | mapping of every service rung to an outcome; second-request decisions (DATA-04); attempt reuse (DATA-50/52) |
| Service | **MockWebServer + real Retrofit + the real converter** | request path, method, headers/tags, body; **every rung of the ladder** incl. malformed 2xx, empty body, unknown code, IO failure |
| Cache / store | virtual time, fakes for session/foreground | single-flight, TTL flip, identity-change clear, pause/resume of polling |
| Interceptors / infra | MockWebServer | headers, retry rules, body still readable after the interceptor |

*Why:* each layer has a cheapest place where its contract can be pinned; testing it higher up is slower and
less precise.

**TEST-21** — Concurrency invariants are tested explicitly, not assumed: N concurrent callers → 1 request
(single-flight); late result of a superseded request is dropped; a cancelled caller cancels its request; a retry
resends the same request id and body.
*Why:* these are the invariants code review misses and production finds.

**TEST-22** — Long-running loops (pollers, tickers) run on `TestScope.backgroundScope`, and suites that start them
use `runTest(timeout = …)`.
*Why:* `runTest` drains pending work before finishing — an endless poll on the test scope spins virtual time
forever; `backgroundScope` is cancelled first.

**TEST-23** — Time is advanced explicitly (`advanceTimeBy`, `advanceUntilIdle`, `runCurrent`); tests never call
`Thread.sleep` or depend on wall-clock time.
*Why:* determinism.

---

## 4. Naming and structure

**TEST-30** — Test class: `<ClassUnderTest>Test`; a large subject MAY be split by behaviour
(`<Subject><UseCase>Test`). Test methods are **backtick sentences stating the behaviour**, often with the reason:
`` `a stale list is treated as no list` ``, `` `a field error clears when that field changes` ``,
`` `an empty list is a success, not a failure` ``.
*Why:* the test report reads as a specification; a failure names the broken promise.

**TEST-31** — Large test classes group cases with `// region <topic>` … `// endregion`. Assertion messages state
intent when the assertion alone does not: `assertEquals("an hour-old list must not be shown as current", …)`.
*Why:* navigable files, self-explaining failures.

**TEST-32** — Tests use the same seams as production (fakes, test dispatchers, `FakeTimeSource`) — no
reflection, no `@VisibleForTesting` back doors into private state.
*Why:* tests that reach around the contract break on refactors that keep behaviour.

---

## 5. Evidence habits (unit and device level)

**TEST-40** — Prove invariants with a **contrast pair**: next to the negative case, a positive control that
differs in exactly one input (*background tick → 0 credential refreshes* next to *user pull → exactly 1*).
*Why:* a lone "nothing happened" passes just as well when the code under test never ran.

**TEST-41** — **Absence of a call** is first-class evidence: assert `fake.calls == 0` after advancing virtual time
past the interval in which the call would have happened — never immediately.
*Why:* absence is only meaningful over a window long enough for presence to have been possible.

---

## 6. What runs where

**TEST-50** — The CI gate runs `./gradlew testDebugUnitTest` (all modules, debug variant) on every pull request;
a failing test blocks merge. Developers run `./gradlew :<module>:testDebugUnitTest` while iterating, and the task
plan names the exact commands (`specs/90-ai-workflow.md` AI-26).
*Why:* the debug unit-test variant covers the logic; running both variants doubles time for almost no signal.

**TEST-51** — A change that adds or alters behaviour adds or updates the test that pins it **in the same
change**. Implementation logs record the command and the counts ("N tests, 0 failures").
*Why:* an untested behaviour change is indistinguishable from a regression.

**TEST-52** — Device-level verification (emulator, scripted responses, request logs, UI dumps, screenshots) is
**on demand only** — run when explicitly asked to verify a task — and follows
`specs/recommended/mitm-testing.md`. Unit tests remain the default verification.
*Why:* device runs are slow and stateful; they confirm integration, they do not replace unit tests.

---

## Shape — a ViewModel test

```kotlin
class ItemListViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val interactor = FakeItemListInteractor()

    private class Callbacks : ItemListViewModelContract.UiCallbacks {
        val visited = mutableListOf<String>()
        override fun goToItem(id: String) { visited += "item:$id" }
    }

    private fun vm() = ItemListViewModel(interactor)

    // region opening an item
    @Test fun `a second tap on another item wins over the first`() = runTest(main.scheduler) {
        interactor.holdOpens = true
        val callbacks = Callbacks()
        val vm = vm().also { it.uiCallbacks = callbacks }
        vm.onItemClick("a"); vm.onItemClick("b")
        interactor.complete("b", OpenOutcome.Ready)
        interactor.complete("a", OpenOutcome.Ready)          // late result of the superseded tap
        advanceUntilIdle()
        assertEquals(listOf("item:b"), callbacks.visited)
        assertEquals(listOf("a"), interactor.cancelledOpens)
    }
    // endregion
}
```

## Shape — a service test

```kotlin
@Before fun setUp() {
    server = MockWebServer().also { it.start() }
    val api: XApi = Retrofit.Builder().baseUrl(server.url("/"))
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build().create()
    service = RetrofitXService(api, TestDispatcherProvider(UnconfinedTestDispatcher()), json)
}
@Test fun `a 2xx without a body is transient, not success`() = runTest {
    server.enqueue(MockResponse().setResponseCode(200))
    assertTrue(service.getX("1", RefreshPolicy.AllowRefresh) is XFetchResult.Transient)
}
```
