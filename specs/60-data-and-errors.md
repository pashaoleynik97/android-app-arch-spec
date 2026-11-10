# 60 — Data and errors (DATA-*)

Tier: **Core** (MUST). Scope: the domain module `:x` of every feature trio — interactors, service seams, DTOs,
mappers, validators, caches/stores, writes — and the error model that connects them to the ViewModel.

What is Core here is the **shape**: who may talk to whom, which outcomes exist, what is caught, what is never
done. Everything that depends on a particular backend (HTTP status → outcome table, error-code names, the
envelope, header names) is marked *example* and lives in the Recommended `network` / `auth-session` specs
(`specs/recommended/network.md`, `specs/recommended/auth-session.md`). An app with no backend still follows
DATA-01…08, DATA-30…32 and DATA-40…46 for its local data.

Related: module layout `specs/10-modules.md`, kernel seams (`Scopes`, `Reporter`, `DispatcherProvider`,
`TimeSource`) `specs/40-kernel.md`, ViewModel shape `specs/50-presentation.md`, tests `specs/70-testing.md`,
recipes `specs/patterns/`.

---

## 1. Domain module shape

```
:x   {{ROOT_PACKAGE}}.<feature>.domain
     XInteractor.kt                 interface XInteractor + class DefaultXInteractor (or split files)
     model/XModels.kt               domain data/enum types (plain Kotlin)
     XValidator.kt                  pure validators (optional)
     XSnapshotStore.kt              flow-scoped store (optional)
     network/XApi.kt                Retrofit interface (only with a backend)
     network/XService.kt            seam interface + classified sealed result(s)
     network/RetrofitXService.kt    implementation = the classifier
     network/XMapper.kt             internal DTO → domain mappers
     network/dto/XDtos.kt           @Serializable wire types
     di/XDomainModule.kt            @Binds / @Provides, SingletonComponent
```

**DATA-01** — A domain module MUST NOT depend on Compose, AndroidX Lifecycle/ViewModel, Navigation, or any
`:*-ui` / `:*-ui-api` module. It depends on `:lib-common` plus the infra modules it needs.
*Why:* the domain is JVM-testable and reusable by any screen; a UI dependency would let presentation decisions
leak into data code and invert the module direction.

**DATA-02** — The interactor is the **only** domain type a ViewModel talks to. It is declared as
`interface XInteractor` and implemented by `class DefaultXInteractor @Inject constructor(...)`, bound with `@Binds`
in `XDomainModule`. ViewModels MUST NOT inject services, APIs, stores, caches' implementations, or session
infrastructure directly (a cache *interface* exposed through the interactor is fine).
*Why:* one seam per feature means one fake per VM test, and every network/session decision has exactly one home.

**DATA-03** — Interactors return **feature-level sealed outcomes** (`sealed interface XOutcome`). They MUST NOT
throw for expected failures, MUST NOT return `kotlin.Result`, HTTP responses, status codes, DTOs or exceptions
to the ViewModel.
*Why:* the VM's state machine must be exhaustive over things the user can experience, checked by the compiler;
exceptions and status codes are invisible in the type and get forgotten.

**DATA-04** — When an outcome can only be decided by a second request (e.g. "the write conflicted — is the item
still alive?"), the **interactor** makes that request and returns the resolved outcome
(`Resynced(...)` / `Unavailable`).
*Why:* a network decision in the middle of a UI state machine is untestable without the UI and duplicated by
every screen that performs the same write.

**DATA-05** — Data a screen needs for chrome (current user's name, avatar URL) is exposed by the interactor,
never fetched by the VM from session infrastructure.
*Why:* keeps DATA-02 true; the VM test needs only the interactor fake.

**DATA-06** — Every source of nondeterminism is an injected seam: ids (`fun interface RequestIdGenerator
{ fun next(): String }`), time (`TimeSource`), dispatchers (`DispatcherProvider`), long-lived scopes (qualified
`CoroutineScope`), and tuning (`data class XConfig(val refreshInterval: Duration, ...)` provided by DI).
A config wrapping a single primitive MUST be a `data class`, not a `@JvmInline value class`.
*Why:* tests control ids, time and scheduling deterministically and can shrink intervals; DI code generation
rejects an injected value class over a primitive.

**DATA-07** — Every collaborator a test needs to replace is an interface (interactor, service, store, cache,
config provider). Pure, stateless logic (validators, mappers, policies, resolvers) stays concrete and is used
directly in tests.
*Why:* fakes replace interfaces; pure code needs no replacement — wrapping it in an interface only adds noise.

**DATA-08** — Every `suspend` function exposed by a domain or infra type is main-safe: it switches to its own
dispatcher (`withContext(dispatchers.io)`) where it blocks. Callers never wrap calls in `withContext`.
*Why:* the VM calls from `viewModelScope` (Main); a single forgotten wrapper is a jank/ANR bug that no type
catches.

**DATA-09** — The DI module is `@Module @InstallIn(SingletonComponent::class) abstract class XDomainModule`
with `@Binds` for implementations and a `companion object` of `@Provides` (API instance from the business
Retrofit, `RequestIdGenerator`, configs). Implementations are `internal` where the binding allows it. Seams the
host must satisfy (e.g. "invalidate a sibling's cache") are declared as a per-feature `fun interface`, left
unbound here and bound in `:app`, with KDoc saying so.
*Why:* one predictable place per feature; a seam bound by the host keeps sibling features from depending on
each other.

---

## 2. Service seam and the classified result

**DATA-10** — Every remote call sits behind `interface XService` whose implementation (`RetrofitXService` or
another transport) **classifies** the response into a sealed result. There is no app-wide generic
`NetworkResult<T>`: each endpoint — or each resource, as `sealed interface XFetchResult<out T>` — owns its own
sealed type listing exactly the outcomes it can produce.
*Why:* a generic wrapper forces every caller to re-interpret status codes; a per-endpoint type makes "what can
go wrong here" a compile-time fact.

**DATA-11** — Results use a shared **vocabulary of rungs, named by consequence**, not by wire detail:

| Rung | Meaning | Who reacts |
|---|---|---|
| `Success(value)` | usable payload, already mapped to domain | feature |
| `SessionExpired` | credentials are dead *after* the transport's own recovery | **host** (feature does nothing) |
| `Refused(code?)` | the server definitively said no to *this request*; retrying will not help | feature: own "no access" message, no retry |
| `Unavailable(reason)` | the thing addressed is gone / moved on / not this user's | feature: leave the screen (exit flow) |
| `Transient(cause?)` | nothing is known — keep what is shown, offer retry | feature |

Write endpoints add **write-only rungs**, each only on the calls that can produce it:

| Rung | Meaning |
|---|---|
| `RetryLater` | an earlier attempt under the same request id is still running — a "wait", not a "no" |
| `StatusChanged` | the target's state moved under the write — ambiguous, see DATA-53 |
| `Rejected(fields)` / `<Gate>Missing(items)` | a definitive business-rule refusal with the data needed to show it |

Several wire reasons that read the same to the user collapse into **one** rung carrying a log-only reason enum
(`Unavailable(reason)`). A scope-level loss of access the host handles app-wide MAY get its own rung
(`ScopeLost`) so the feature can recognise and swallow it.
*Why:* the VM branches on what the user experiences; reasons stay diagnosable in logs without multiplying UI
branches.

**DATA-12** — Anything ambiguous is `Transient`: timeouts, IO errors, 5xx, unexpected 4xx, a 2xx with a missing,
empty or unmappable body, a body that fails to deserialize. A 2xx that cannot be mapped is **never** `Success`
with defaults, never `Unavailable`, never a crash; it is logged via `Reporter.error` with the endpoint.
*Why:* only a definitive answer may change what the user is allowed to do; a server bug must read as "try again",
not as "your item is gone".

**DATA-13** — Services catch **only** `IOException` and `SerializationException`. They MUST NOT catch
`Exception`, `Throwable`, or use `runCatching` around suspend calls.
*Why:* `CancellationException` must propagate — leaving a screen or superseding a tap cancels the coroutine,
which cancels the HTTP call; swallowing it turns cancellation into a bogus `Transient` and keeps dead work alive.
Anything else is a programming error that should crash in development.

**DATA-14** — An error body is read **once** per response, through one helper that returns `null` on any parse
failure (`parseApiError(response, json): ApiErrorDetails?`). Interceptors and authenticators that need to look
at a body MUST use `peekBody(limit)`, never consume it.
*Why:* `errorBody()` is a single-read stream; a second read (or an interceptor that consumed it) silently
yields nothing and misclassifies the response.

**DATA-15** — Classification branches first on **HTTP status class/status**, then refines by error code within
that status. Where two statuses mean different things to the app (e.g. "token dead" vs "access refused") they
MUST map to different rungs regardless of the code carried in the body.
*Why:* branching on the code alone lets a generic code under the wrong status fall into the wrong branch — e.g. a
plain unauthorized response treated as an access refusal.

**DATA-16** — The service's class KDoc documents its ladder as a bullet list (status/condition → rung), and the
service logs unexpected statuses with `Reporter.error("<endpoint> → <code>", where = logTag)`.
*Why:* the ladder is the contract between backend and app; reviewers and QA need it in one place.

**DATA-17** — An **ambiguous failure never ends a session, never discards user input, and never drops a pending
write attempt.** Only a definitive answer (`SessionExpired` after recovery, `Refused`, `Unavailable`,
`Rejected`) may do any of those.
*Why:* a flaky network must not log a user out or lose their work; false logouts and lost forms are the most
expensive bugs this layer can produce.

**DATA-18** — Outcomes the **host** owns (`SessionExpired`, app-wide scope loss) are swallowed by feature
screens: the VM stops its spinner and shows nothing, because the host is already navigating. Feature-specific
refusals are rendered by the feature.
*Why:* two components reacting to the same event produce double navigation and duplicate banners.

**DATA-19** — If the app has refreshable credentials, a service method that can be called both by a timer and by
the user MUST let the caller state which (example: a `RefreshPolicy` request tag — see
`specs/recommended/auth-session.md`). Timer-driven calls never extend a session.
*Why:* a background poll that silently refreshes tokens keeps an idle device logged in forever.

Shape (example contract — the status mapping is backend-specific):

```kotlin
/**
 * Ladder: 2xx+mappable → Success · 2xx unmappable → Transient · 401 → SessionExpired ·
 * 403 scope-level code → ScopeLost · other 403 → Refused · 404/410 → Unavailable(GONE) · else → Transient.
 */
internal class RetrofitXService @Inject constructor(
    private val api: XApi,
    private val dispatchers: DispatcherProvider,
    private val json: Json,
) : XService {
    override suspend fun getX(id: String, policy: RefreshPolicy): XFetchResult<X> = withContext(dispatchers.io) {
        try {
            val r = api.getX(id, policy)
            when {
                r.isSuccessful -> r.body()?.data?.toDomain()?.let { XFetchResult.Success(it) }
                    ?: XFetchResult.Transient(null).also { Reporter.error("GET x/$id: unusable 2xx", where = logTag) }
                r.code() == 401 -> XFetchResult.SessionExpired
                r.code() == 403 -> if (parseApiErrorCode(r, json) in SCOPE_LEVEL) XFetchResult.ScopeLost
                                   else XFetchResult.Refused(parseApiErrorCode(r, json))
                r.code() == 404 || r.code() == 410 -> XFetchResult.Unavailable(XUnavailableReason.GONE)
                else -> XFetchResult.Transient(null).also { Reporter.error("GET x/$id → ${r.code()}", where = logTag) }
            }
        } catch (e: IOException) {
            XFetchResult.Transient(e)
        } catch (e: SerializationException) {
            XFetchResult.Transient(e)
        }
    }
}
```

(Parse the error code once into a local in real code — DATA-14; the inline double call above is for brevity.)

---

## 3. DTOs, mapping, domain models

**DATA-20** — Wire types are `@Serializable data class …Dto` in `network/dto/`, separate from domain models.
Every optional field has a default. The app shares **one** configured `Json` instance (unknown keys ignored,
lenient where the backend requires it) provided by DI.
*Why:* a new server field or an omitted optional one must not break deserialization of an otherwise valid
payload; two `Json` instances drift.

**DATA-21** — Enumerations on the wire are carried as `String` in DTOs and converted with a total function —
`fromWire(code) = entries.firstOrNull { it.wire == code } ?: UNKNOWN` (or `null`). An unknown value degrades
(safe default, item dropped, or rendered generically); it MUST NOT throw.
*Why:* the server ships new values before the app does; one unknown value must not fail an entire list.

**DATA-22** — Mapping is `internal fun XDto.toDomain(): X` (or `X?` to drop an item that cannot be shown) in
`network/XMapper.kt`. Derived values (buckets, states, flags) are re-derived from raw fields with shared
helpers rather than trusting a duplicated summary field. Data-quality gaps are logged with `Reporter.warn`
**once per id**, not on every poll.
*Why:* `internal` keeps DTOs from leaking; derived values computed in one place cannot disagree between screens;
per-poll warnings drown the log.

**DATA-23** — Domain models are plain Kotlin data classes / enums with KDoc per field, no Android types, no
serialization annotations. Values that need exact arithmetic (money, measured quantities) use `BigDecimal`.
*Why:* the model is what the VM and tests reason about; it must not change when the wire format does.

**DATA-24** — Infra modules expose raw error codes (`String?`); each feature maps codes to its own vocabulary.
Infra modules stay free of feature or domain words.
*Why:* a shared module that knows feature codes becomes a dependency magnet every feature must edit.

---

## 4. Validators

**DATA-30** — Validators are pure and stateless (`object XValidator` or an `@Inject constructor()` class with no
state) and return **typed** errors (enums / sealed types), aggregated into a result with a derived
`isValid`. They MUST NOT return strings or resource ids.
*Why:* typed errors are exhaustively testable on the JVM and localised by the composable, which owns resources.

**DATA-31** — The VM validates **on submit**; a field's error is cleared when that field changes; input
filtering (allowed characters, max length) happens in the `onXChange` intent, before state is updated.
*Why:* errors that appear while the user is still typing are noise; filtering at the intent keeps impossible
input out of state entirely.

**DATA-32** — Client validation is advisory. The server's own business-rule refusal is still classified
(`Rejected(fields)`) and rendered; when the refusal payload is in a shape the app does not recognise, the
refusal is still shown, just without field detail.
*Why:* client and server rules drift; the server is the authority and its "no" must never be lost.

---

## 5. Caches and stores

**DATA-40** — Every data holder is one of three kinds, chosen by lifetime:

| Kind | Lifetime | Implementation | Example use |
|---|---|---|---|
| App-wide cache | process; cleared when identity changes | `@Singleton` with its own qualified `CoroutineScope(SupervisorJob() + …)`, `StateFlow<Snapshot>` | a list shown on many screens and kept warm |
| Flow snapshot store | one user flow (root screen → its sub-screens) | typed store over `Scopes`, keyed by entity id, cleared by the flow's root VM `onCleared()` | the read model a details screen hands to its item screens |
| Persisted store | across process death | infra module (DataStore/Keystore/files) | session, settings |

Feature domain modules hold in-memory kinds only; persistence lives in infra modules.
*Why:* lifetime is the decision that causes bugs (stale data after logout, leaks, lost state); naming the three
kinds makes the choice explicit.

**DATA-41** — **A cache is not a source of truth.** Every consumer tolerates an absent or stale entry and falls
back to one read; after a mutation, the authoritative re-read still happens.
*Why:* process death, a cleared scope, or a second device can invalidate any in-memory copy at any time.

**DATA-42** — In-memory entries are keyed by entity id (no single global slot), and have an explicit owner that
clears them (the flow root's `onCleared`, an identity change for app-wide caches).
*Why:* two open items must not see each other's data; an entry with no owner is a leak and a stale-data bug.

**DATA-43** — What travels where: **ids → route arguments; large read models → a snapshot store; results →
the previous back-stack entry's saved state.** Anything that must survive process death MUST NOT live only in
`Scopes` or a store.
*Why:* routes and saved state are restored by the system; `Scopes` is not.

**DATA-44** — An app-wide cache exposes a snapshot in which "nothing loaded yet", "stale", "refreshing" and
"last failure" are **states**, not errors. A failed refresh keeps the last data. Staleness (TTL) is flipped by an
explicitly scheduled job because time passing emits nothing. In-session TTLs use the monotonic `TimeSource`;
TTLs that must survive a reboot use the wall clock. The cache drops its content **before** anything else when
the identity it belongs to (user, tenant) changes.
*Why:* screens render from the snapshot alone; a screen sitting on a snapshot would otherwise believe it fresh
forever; showing the previous user's data is a privacy bug.

**DATA-45** — Forced refreshes are **single-flight**: concurrent callers share one in-flight `Deferred` launched
on the cache's own scope (so a caller that navigates away still gets the result written). A user-driven refresh
never rides on a background-only request that may not refresh credentials.
*Why:* N taps must cost one request; a user waiting deserves the attempt that can actually succeed.

**DATA-46** — Persisted stores MUST NOT throw on corrupt or unreadable data: they return `null` / a safe default
and `Reporter.warn`.
*Why:* a crash loop on start-up from one bad byte is unrecoverable for the user.

**DATA-47** — A mutation's response is written back into the relevant snapshot store (write-through) so the
previous screen repaints immediately on return; that screen's own re-read follows and stays authoritative.
*Why:* instant feedback without trusting the copy (DATA-41).

---

## 6. Writes

**DATA-50** — Every non-idempotent write carries a **request id** (idempotency key). The id is minted **once per
attempt** — when the user commits the action — through `RequestIdGenerator`, stored together with the exact
request body, and **resent unchanged** on every retry until a definitive answer (success or a definitive
rejection). Then the attempt is dropped. A new user action mints a new attempt.
*Why:* a resend after a timeout must be recognised by the server as the same action; minting a new id per retry
turns a lost response into a duplicate write, and changing the body under the same id is refused as key reuse.

**DATA-51** — Retry policy: `Transient` keeps the attempt (the user's retry resends it); `RetryLater` triggers a
**bounded** automatic backoff (`delay(BACKOFF)`, at most `MAX_RETRIES`) resending the same attempt, after which
it surfaces as `Transient`. Definitive rungs drop the attempt.
*Why:* "still processing" resolves on its own; unbounded loops do not.

**DATA-52** — Optimistic-concurrency tokens (version, `updatedAt`, ETag) are captured **from the state the user
saw and confirmed** — the read that backed the confirmation screen — stored in the attempt, and echoed verbatim.
A retry MUST NOT re-read the token.
*Why:* re-reading turns a safe resend into a second transition, or commits against a version the user never
saw — exactly what the lock exists to prevent.

**DATA-53** — "Status changed" means different things on a read and on a write. On a **read** it means the item
left the user's reach → `Unavailable`. On a **write** it is ambiguous (someone else may have legitimately moved
the item while it stays valid) → the interactor re-reads (DATA-04) and resolves to `Resynced(fresh)` or
`Unavailable`.
*Why:* folding a write conflict into "gone" ejects users from work that is still theirs.

**DATA-54** — At most one write attempt per action is in flight; intents check an in-flight flag first and
return. For per-row actions, state holds `inFlightIds: Set<Id>` and attempts in a `Map<Id, Attempt>`.
*Why:* double taps must not send two different attempts.

---

## 7. Concurrency

**DATA-60** — Cancellation propagates from the VM through the interactor and service to the HTTP call
(DATA-13). "Last action wins" flows cancel the previous job and drop late results with a generation counter.
*Why:* a superseded request must neither keep the network busy nor overwrite newer state.

**DATA-61** — Every long-lived scope is `CoroutineScope(SupervisorJob() + dispatcher)`, provided `@Singleton`
under a module-owned qualifier, and injected — never created inline — so tests can substitute a test scope.
*Why:* one failing child must not kill the scope; tests must own scheduling.

**DATA-62** — Background machinery that must run from process start (an app-wide cache, an access-loss filter)
is constructed eagerly by the host (injected into `Application`), not lazily by the first screen that touches it.
*Why:* lazy construction makes behaviour depend on navigation order and races background callbacks.

---

## Adapting this to your app

- **Core, keep as is:** DATA-01…18, 20…24, 30…32, 40…47, 50…54, 60…62.
- **Example only:** the status → rung ladder above, error-code names, the envelope type and its helpers
  (`ApiResponse<T>`, `parseApiError`), `RefreshPolicy`. Adapt them in the `network` / `auth-session`
  components to your backend; the rung *vocabulary* stays.
- No backend at all: there are no services; interactors still return sealed outcomes over local stores.
- Patterns that combine these rules: `specs/patterns/app-wide-cached-list.md`,
  `specs/patterns/details-item-finish.md`, `specs/patterns/paginated-picker.md`.
