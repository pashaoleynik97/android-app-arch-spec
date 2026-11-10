# Kernel — Reporter, Scopes, foreground watch, seams, stores (component: architecture)

Everything here lives in `:lib-common` (package `{{ROOT_PACKAGE}}.libcommon`). Spec:
`.arch-spec/spec/specs/40-kernel.md`. Golden rules in `SKILL.md`: KERNEL-01 (Reporter only), KERNEL-09
(transport by process-death survival), KERNEL-15 (main-safe suspend APIs), KERNEL-16 (cancellation propagates).

Contents: 1. Reporter · 2. Scopes · 3. AppForegroundWatch · 4. Seams · 5. Stores · 6. Utilities

---

## 1. Reporter — the only logging path

```kotlin
object Reporter {                                   // logging/Reporter.kt
    interface ExternalReporter { fun logReport(message: String); fun recordException(exception: Throwable) }
    fun addExternalReporter(reporter: ExternalReporter)
    fun removeExternalReporter(reporter: ExternalReporter)
    fun removeAllExternalReporters()
    fun debug(message: String, where: String)
    fun info(message: String, where: String)
    fun warn(message: String, exception: Throwable? = null, where: String)
    fun error(message: String, exception: Throwable? = null, where: String)
}
private val logTag = logTag()                       // logging/LogTag.kt: the class's simple name
Reporter.warn("refresh failed", e, where = logTag)
```

**KERNEL-02** — Call convention: `where = logTag`, with `private val logTag = logTag()` in the class
(BaseViewModel already exposes a protected `logTag`). All local output uses one Android log tag; the caller is
identified by the `[where]` prefix. *Why:* one filterable tag, and every line says where it came from without
a hand-typed string.
<!-- origin: arch-spec KERNEL-02 -->

**KERNEL-03** — Local logcat output is emitted only in debug builds; external reporters receive every call in
every build type (`debug` / `info` as `logReport`, `warn` / `error` as `recordException`, wrapping the message
in a ReportedException when no exception is given). *Why:* release logcat is readable by anyone with a cable;
crash-reporting breadcrumbs are exactly what release needs.
<!-- origin: arch-spec KERNEL-03 -->

**KERNEL-04** — External reporters (crash reporting, analytics breadcrumbs) are registered by the host at
startup with `addExternalReporter`. The set is copy-on-write, and a throwing reporter never breaks logging for
the others or for the caller. *Why:* logging is called from every thread; a reporter crash must never become
an app crash.
<!-- origin: arch-spec KERNEL-04 -->

**KERNEL-05** — Credentials and secrets (passwords, tokens, one-time codes, personal identifiers) are never
logged nor kept in UI state; text that can contain them (HTTP bodies) is masked before it reaches the
Reporter. *Why:* external reporters get release logs; a credential in a breadcrumb sits on a third party's
server.
<!-- origin: arch-spec KERNEL-05 -->

**KERNEL-06** — `warn` logs at warning level, `error` at error level; `exception` is a named parameter
defaulting to `null`. *Why:* misleading levels make filtering by severity useless.
<!-- origin: arch-spec KERNEL-06 -->

**KERNEL-07** — Tests that assert on logging use the RecordingReporter rule from `:core-testing`
(`@get:Rule val reporter = RecordingReporter()` — it registers itself before each test and removes itself
after); a hand-rolled reporter is removed in `@After`. JVM tests rely on the conventions'
`isReturnDefaultValues` for the `Log` calls. *Why:* the Reporter is a global object; a missing teardown makes
tests order-dependent.
<!-- origin: arch-spec KERNEL-07 -->

## 2. Scopes — flow-scoped in-memory state

```kotlin
@Singleton class Scopes @Inject constructor() {     // context/Scopes.kt
    interface Scope
    interface ScopeKey<T : Scope> { val id: String; fun create(): T }
    fun <T : Scope> getOrOpenScope(key: ScopeKey<T>): T
    fun <T : Scope> updateScope(key: ScopeKey<T>, scope: T)
    fun <T : Scope> peekScope(key: ScopeKey<T>): T?
    fun <T : Scope> consumeScope(key: ScopeKey<T>): T?   // read + close
    fun isOpen(key: ScopeKey<*>): Boolean
    fun closeScope(key: ScopeKey<*>)
    fun closeAll()
}
// in a feature:
data class PickerScope(val selected: Set<String> = emptySet()) : Scopes.Scope
object PickerKey : Scopes.ScopeKey<PickerScope> { override val id = "picker-selection"; override fun create() = PickerScope() }
```

**KERNEL-08** — Scopes is a process-wide, keyed, in-memory holder for state shared by the screens of one flow.
Features declare their own keys and scope types; Scopes itself never changes. Key ids are globally unique,
prefixed with the feature (`"orders-snapshot:$id"`). *Why:* feature-owned keys keep the kernel closed for
modification; unique ids are all that stands between two features and an unchecked-cast crash.
<!-- origin: arch-spec KERNEL-08 -->

**KERNEL-10** — Consumers of a Scopes value tolerate `null` (the producer's process died): re-read from the
source by id, or navigate back to the producing step. The flow's root ViewModel owns the lifetime and closes
the scope in `onCleared()`; one-shot handoffs use `consumeScope`. *Why:* a cache is not a source of truth, and
an unclosed scope leaks a whole flow's data for the life of the process.
<!-- origin: arch-spec KERNEL-10 -->

**KERNEL-11** — Anything richer than a one-shot handoff wraps Scopes in a typed, fakeable store keyed per
entity id (`interface OrderSnapshotStore { fun peek(id: String): Order?; fun put(id: String, order: Order);
fun clear(id: String) }`), and mutation responses are written through it so the previous screen repaints on
return while its own re-read stays authoritative. *Why:* typed call sites, no cross-talk between two open
entities, and a store a test can replace.
<!-- origin: arch-spec KERNEL-11 -->

## 3. AppForegroundWatch

**KERNEL-12** — `AppForegroundWatch.isForeground: StateFlow<Boolean>` is the single process-wide "should
background work run?" signal. DefaultAppForegroundWatch observes the process lifecycle (registering on the
main thread) and is an eager singleton field-injected into the Application (DI-14). *Why:* one definition of
"foreground" for every poller; the interface lets jobs be tested against a fake.
<!-- origin: arch-spec KERNEL-12 -->

**KERNEL-13** — Periodic background work (polling caches, keep-alives) pauses while `isForeground` is false and
refreshes immediately on return (`collectLatest` over the signal). *Why:* polling in the background drains
battery and keeps alive server sessions the user abandoned.
<!-- origin: arch-spec KERNEL-13 -->

## 4. Seams for nondeterminism

```kotlin
interface DispatcherProvider { val io: CoroutineDispatcher; val default: CoroutineDispatcher }   // coroutines/
interface TimeSource { fun elapsedRealtimeMillis(): Long }    // time/ — monotonic: scheduling, in-process TTLs
```
Bound in KernelModule to DefaultDispatcherProvider and SystemClockTimeSource; tests use TestDispatcherProvider
and FakeTimeSource from `:core-testing`.

**KERNEL-14** — Code under test never reads dispatchers or clocks directly: it injects DispatcherProvider and
TimeSource (plus a wall-clock seam where calendar time matters, e.g. a TTL that must survive a reboot); a module
with its own dispatcher or scope injects a qualified one (DI-10); randomness tests must control (request ids)
sits behind a `fun interface`. *Why:* a monotonic clock is immune to clock changes, and injected clocks and
dispatchers let tests use virtual time instead of sleeping.
<!-- origin: arch-spec KERNEL-14 -->

## 5. Stores

**KERNEL-17** — A store (persisted settings, tokens, profile) never throws on corrupt or unreadable data: it
returns `null` or a documented safe default and reports through `Reporter.warn` / `Reporter.error`.
*Why:* a crash loop on launch caused by one bad preference is unrecoverable for the user.
<!-- origin: arch-spec KERNEL-17 -->

**KERNEL-18** — Persistence is Store → Repository: a main-safe `suspend` store interface, a `@Singleton`
repository exposing a `StateFlow` on an injected app scope, setters that update the flow first and then
persist. A flow that drives side effects starts as `null` ("not loaded yet"), never a guessed default.
*Why:* a guessed default triggers the side effect for the wrong value before the real one loads.
<!-- origin: arch-spec KERNEL-18 -->

## 6. Utilities

**KERNEL-19** — Date and time: with a `minSdk` below the level that ships `java.time`, the project decides
explicitly — core-library desugaring plus `java.time`, or small tested parsing helpers in the kernel — and
records the decision (an ADR). `SimpleDateFormat` and default-time-zone parsing are not used for wire formats.
*Why:* `java.time` crashes on old devices without desugaring; `SimpleDateFormat` is not thread-safe and
silently uses the device zone.
<!-- origin: arch-spec KERNEL-19 -->

**KERNEL-20** — The kernel ships only utilities with call sites; speculative helpers are not added "for
later". *Why:* advertised-but-unused helpers mislead agents into patterns the codebase does not follow.
<!-- origin: arch-spec KERNEL-20 -->
