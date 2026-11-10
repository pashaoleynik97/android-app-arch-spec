# 40 — Kernel: Reporter, Scopes, foreground watch, seams, main-safety (KERNEL)

Tier: **Core** (component `architecture`). All types here live in `:lib-common`
(`{{ROOT_PACKAGE}}.libcommon.*`), which has no Compose and no UI (MOD-04).

Contents: 1. Reporter · 2. Scopes and the state-transport decision · 3. AppForegroundWatch · 4. Seams for
nondeterminism · 5. Main-safety and errors · 6. Stores and repositories · 7. Utilities

---

## 1. Reporter — the only logging path

```kotlin
object Reporter {
    abstract class ExternalReporter(private val id: String = UUID.randomUUID().toString()) {
        abstract fun logReport(msg: String)
        abstract fun recordException(e: Throwable)
    }
    fun addExternalReporter(reporter: ExternalReporter)
    fun removeAllExternalReporters()
    fun debug(description: String, where: String)
    fun info(description: String, where: String)
    fun warn(message: String? = null, exception: Throwable? = null, where: String)
    fun error(message: String? = null, exception: Throwable? = null, where: String)
}
```

**KERNEL-01** — All logging MUST go through `Reporter`. `android.util.Log` appears only inside `Reporter`; no
`println`, no direct third-party logger calls in feature or infra code (HTTP logging included: the OkHttp logging
interceptor writes through a `Reporter`-backed logger).
*Why:* one seam is where debug gating, masking and crash-reporter fan-out happen; a single bypass leaks
credentials or loses a crash.

**KERNEL-02** — Call convention: `Reporter.debug("message", where = logTag)` with
`private val logTag = logTag()` (`fun Any.logTag(): String` = the class's simple name). `BaseViewModel` exposes
`protected val logTag`. All local output uses one Android log tag; the caller is identified by the `[where]`
prefix.
*Why:* one filterable tag in logcat, and every line says where it came from without a hand-typed string.

**KERNEL-03** — Local logcat output is emitted **only in debug builds** (`BuildConfig.DEBUG` of `:lib-common`).
External reporters get every call **in every build type**: `debug`/`info` as `logReport`, `warn`/`error` as
`recordException` (wrapping the message when no exception is given).
*Why:* release logcat is readable by anyone with a cable; crash-reporting breadcrumbs are exactly what release
needs.

**KERNEL-04** — External reporters (crash reporting, analytics breadcrumbs) are registered by the host at startup
with `addExternalReporter`. The reporter set is thread-safe (copy-on-write); a throwing reporter MUST NOT
break logging for the others or for the caller.
*Why:* logging is called from every thread; a reporter crash must never become an app crash.

**KERNEL-05** — Credentials and secrets (passwords, tokens, one-time codes, personal identifiers) MUST NEVER be
logged or kept in UI state. Where a credential can appear inside text that is logged (HTTP bodies), a masking
function is applied before the text reaches `Reporter`.
*Why:* external reporters get release logs (KERNEL-03); a credential in a breadcrumb is a credential on a third
party's server.

**KERNEL-06** — `warn` logs at warning level and `error` at error level. `exception` is a named parameter with a
`null` default.
*Why:* misleading levels make filtering by severity useless.

**KERNEL-07** — Tests that assert on logging install a recording `Reporter.ExternalReporter` in `@Before` and call
`removeAllExternalReporters()` in `@After` (the recorder lives in `:core-testing`). JVM tests rely on
`isReturnDefaultValues = true` (BUILD-15) for the `Log` calls.
*Why:* `Reporter` is a global object; forgetting the teardown makes tests order-dependent.

## 2. Scopes — flow-scoped in-memory state, and choosing a transport

```kotlin
@Singleton
class Scopes @Inject constructor() {
    interface Scope
    interface ScopeKey<T : Scope> { val id: String; fun create(): T }
    @Synchronized fun <T : Scope> getOrOpenScope(key: ScopeKey<T>): T
    @Synchronized fun <T : Scope> updateScope(key: ScopeKey<T>, scope: T)
    @Synchronized fun <T : Scope> peekScope(key: ScopeKey<T>): T?
    @Synchronized fun <T : Scope> consumeScope(key: ScopeKey<T>): T?   // read + close
    @Synchronized fun isOpen(key: ScopeKey<*>): Boolean
    @Synchronized fun closeScope(key: ScopeKey<*>)
    @Synchronized fun closeAll()
}
```

**KERNEL-08** — `Scopes` is a process-wide, keyed, **in-memory** holder for state shared by the screens of one
flow. Features declare their own keys (`object PickerKey : Scopes.ScopeKey<PickerScope>`,
`data class PickerScope(…) : Scopes.Scope`); `Scopes` itself never changes. Key ids MUST be globally unique
(prefix with the feature: `"orders-snapshot:$id"`).
*Why:* keys owned by features keep the kernel closed for modification; ids are the only thing standing
between two features and an unchecked-cast crash.

**KERNEL-09** — Pick the transport by **what must survive process death**:

| Data | Transport | Survives process death |
|---|---|---|
| ids needed to (re)load a screen | route arguments (UI-19) | yes |
| a screen's own transient UI state | the ViewModel; `SavedStateHandle` only for what must be restored | yes (saved state) |
| a result returned to the previous screen | previous back-stack entry's `SavedStateHandle` (UI-21) | yes |
| a large read model shared by screens of one flow | `Scopes` / a typed store over it | **no** |
| anything that must persist across launches | a store in a domain or infra module | yes (disk) |

Anything that must survive process death MUST NOT live **only** in `Scopes` (ADR-0004).
*Why:* `Scopes` is gone after the system kills a backgrounded app; a flow that depends on it alone shows empty
screens or crashes on restore.

**KERNEL-10** — Consumers of a `Scopes` value MUST tolerate `null` (the producer's process died): re-read from
the source by id, or navigate back to the producing step. The flow's **root** ViewModel owns the lifetime and
closes the scope in `onCleared()` (or the consumer uses `consumeScope` for one-shot handoffs).
*Why:* a cache is not a source of truth; an unclosed scope leaks a whole flow's data for the life of the process.

**KERNEL-11** — For anything richer than a one-shot handoff, wrap `Scopes` in a typed, fakeable store keyed per
entity id (`interface OrderSnapshotStore { fun peek(id): Order?; fun put(id, order); fun clear(id) }`), and write
mutation responses through it so the previous screen repaints instantly on resume while its own re-read stays
authoritative.
*Why:* call sites stay typed, two open entities never cross-talk, and the store is replaceable by a fake.

## 3. AppForegroundWatch

**KERNEL-12** — `interface AppForegroundWatch { val isForeground: StateFlow<Boolean> }` is the single process-wide
"should background work run?" signal. The default implementation observes `ProcessLifecycleOwner` (registering on
the main thread even if Hilt constructs it elsewhere) — `onStart` → `true`, `onStop` → `false` — and is an eager
singleton (DI-14).
*Why:* one definition of "foreground" for every poller; the interface lets jobs be tested against a fake.

**KERNEL-13** — Periodic background work (polling caches, keep-alives) MUST pause while `isForeground` is false and
SHOULD refresh immediately on return to foreground (`collectLatest` over the signal).
*Why:* polling in the background drains battery and keeps server sessions alive that the user abandoned.

## 4. Seams for nondeterminism

**KERNEL-14** — Code under test never reads dispatchers or clocks directly. The kernel provides:
```kotlin
interface DispatcherProvider { val io: CoroutineDispatcher; val default: CoroutineDispatcher }
interface TimeSource { fun elapsedRealtimeMillis(): Long }     // monotonic: scheduling, TTLs in-process
```
plus a wall-clock seam where calendar time matters (a TTL that must survive a reboot). Modules that need a
private dispatcher or scope inject a qualified one instead (DI-10). Randomness that tests must control
(request ids) goes behind a `fun interface` too.
*Why:* a monotonic clock is immune to NTP and user clock changes; injected clocks and dispatchers let tests use
virtual time instead of sleeping.

## 5. Main-safety and errors

**KERNEL-15** — Every `suspend` function in a domain or infra API is **main-safe**: it switches to its own
dispatcher (`withContext(dispatchers.io)`) for blocking work. Callers never wrap a call in `withContext`.
*Why:* the ViewModel calls from `viewModelScope` on Main; if safety depends on each caller remembering, one
forgets and the UI janks or `StrictMode` kills the app.

**KERNEL-16** — `CancellationException` MUST propagate. Catch only the specific exceptions a layer can classify
(`IOException`, `SerializationException`); never `catch (e: Exception)` around a suspend call without rethrowing
cancellation.
*Why:* swallowing cancellation turns "last tap wins" and screen-exit cancellation into zombie work that writes
stale state.

## 6. Stores and repositories

**KERNEL-17** — A store (persisted settings, tokens, profile) MUST NOT throw on corrupt or unreadable data: it
returns `null` or a documented safe default and reports through `Reporter.warn/error`.
*Why:* a crash loop on launch caused by one bad preference is unrecoverable for the user.

**KERNEL-18** — The persistence pattern is **Store → Repository**: a `suspend` main-safe store interface, a
`@Singleton` repository exposing `StateFlow` on an injected app scope, and setters that update the flow first
and then persist. A flow that **drives side effects** starts as `null` ("not loaded yet"), not as a guessed
default; a flow that only renders MAY start from defaults.
*Why:* a guessed default triggers the side effect for the wrong value before the real one loads.

## 7. Utilities

**KERNEL-19** — Date/time: with `minSdk` below the level that ships `java.time`, the project MUST decide
explicitly — either enable core-library desugaring and use `java.time`, or keep small tested parsing helpers in
the kernel — and record the decision. `SimpleDateFormat` and default-time-zone parsing are not used for wire
formats.
*Why:* `java.time` crashes on old devices without desugaring; `SimpleDateFormat` is not thread-safe and silently
uses the device zone.

**KERNEL-20** — The kernel ships only utilities with call sites. Speculative helpers (weak-reference wrappers,
timer loops, unused accessors) are not added "for later".
*Why:* advertised-but-unused helpers mislead agents into patterns the codebase does not actually follow.
