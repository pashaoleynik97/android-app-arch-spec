# Data and errors — domain modules, sealed outcomes, caches, writes (component: architecture)

Spec: `.arch-spec/spec/specs/60-data-and-errors.md`. Golden rules in `SKILL.md`: DATA-03 (sealed outcomes, no
exceptions or HTTP types to the ViewModel), KERNEL-16 (cancellation propagates). The **shape** below is Core;
the HTTP status ladder, error-code names and envelope are an *example* contract adapted by the network /
session components when installed. An app with no backend still follows sections 1, 4 and 5 for local data.

Contents: 1. Domain module shape · 2. Service seam and classified results · 3. DTOs and mapping ·
4. Validators · 5. Caches and stores · 6. Writes · 7. Concurrency

---

## 1. Domain module shape

```
x/domain/XInteractor.kt, DefaultXInteractor.kt     interface + implementation (the only type a VM sees)
x/domain/model/XModels.kt                          plain Kotlin data / enum types
x/domain/XValidator.kt                             pure validators (optional)
x/domain/network/XApi.kt, XService.kt, RetrofitXService.kt, XMapper.kt, dto/XDtos.kt   (with a backend)
x/domain/di/XDomainModule.kt                       @Binds / @Provides, singleton component
```

**DATA-01** — A domain module depends on `:lib-common` plus the infra it needs — never on Compose, AndroidX
Lifecycle / ViewModel, Navigation or any UI module. *Why:* the domain stays JVM-testable and reusable, and
presentation decisions cannot leak into data code.
<!-- origin: arch-spec DATA-01 -->

**DATA-02** — The interactor (`interface XInteractor` + `DefaultXInteractor @Inject constructor`, bound with
`@Binds`) is the only domain type a ViewModel talks to; ViewModels never inject services, APIs, stores or
session infrastructure. *Why:* one seam per feature means one fake per ViewModel test and one home for every
network or session decision.
<!-- origin: arch-spec DATA-02 -->

**DATA-04** — When an outcome can only be decided by a second request ("the write conflicted — is the item
still there?"), the interactor makes that request and returns the resolved outcome. *Why:* a network decision
inside a UI state machine is untestable without the UI and duplicated by every screen.
<!-- origin: arch-spec DATA-04 -->

**DATA-05** — Data a screen needs for chrome (current user's name, avatar) is exposed by the interactor, never
fetched by the ViewModel from session infrastructure. *Why:* keeps DATA-02 true.
<!-- origin: arch-spec DATA-05 -->

**DATA-06** — Every source of nondeterminism is injected: ids (`fun interface RequestIdGenerator`), time
(TimeSource), dispatchers (DispatcherProvider), long-lived scopes (qualified), tuning (a `data class XConfig`).
A single-value config is a `data class`, not a value class. *Why:* tests control ids, time and scheduling and
can shrink intervals; KSP rejects an injected value class over a primitive.
<!-- origin: arch-spec DATA-06 -->

**DATA-07** — Collaborators a test replaces are interfaces (interactor, service, store, cache, config
provider); pure stateless logic (validators, mappers, policies) stays concrete. *Why:* fakes replace
interfaces; wrapping pure code in an interface only adds noise.
<!-- origin: arch-spec DATA-07 -->

**DATA-08** — Every `suspend` function of a domain or infra type is main-safe through its injected dispatcher
(`withContext(dispatchers.io)`); callers never wrap calls. *Why:* a forgotten wrapper is a jank bug no type
catches (KERNEL-15).
<!-- origin: arch-spec DATA-08 -->

**DATA-09** — The DI module is `@Module @InstallIn(SingletonComponent::class) abstract class XDomainModule`
with `@Binds` for implementations and a `companion object` of public `@Provides`; implementations `internal`
where possible; host-bound ports are declared here, left unbound and documented (MOD-19, DI-09).
*Why:* one predictable place per feature.
<!-- origin: arch-spec DATA-09 -->

## 2. Service seam and classified results

**DATA-10** — Every remote call sits behind `interface XService` whose implementation classifies the response
into the endpoint's (or resource's) own sealed result. There is no app-wide generic result wrapper.
*Why:* a generic wrapper makes every caller re-interpret status codes; a per-endpoint type makes "what can go
wrong here" a compile-time fact.
<!-- origin: arch-spec DATA-10 -->

**DATA-11** — Results use one vocabulary of rungs named by consequence:

| Rung | Meaning | Who reacts |
|---|---|---|
| Success(value) | usable payload, mapped to domain | feature |
| SessionExpired | credentials dead after the transport's own recovery | host (feature does nothing) |
| Refused(code?) | a definitive "no" to this request | feature: its own message, no retry |
| Unavailable(reason) | the addressed thing is gone / moved on / not this user's | feature: leave the screen |
| Transient(cause?) | nothing is known | feature: keep what is shown, offer retry |

Writes add RetryLater (an earlier attempt with the same request id is still running), StatusChanged
(ambiguous, DATA-53) and Rejected(fields) / a gate-missing rung, only on calls that can produce them. Several
wire reasons that read the same to the user collapse into one rung carrying a log-only reason enum.
*Why:* the ViewModel branches on what the user experiences; reasons stay diagnosable in logs.
<!-- origin: arch-spec DATA-11 -->

**DATA-12** — Anything ambiguous is Transient: timeouts, IO errors, 5xx, unexpected 4xx, a 2xx with a missing,
empty or unmappable body. An unmappable 2xx is never Success-with-defaults, never Unavailable, never a crash;
it is logged with `Reporter.error` naming the endpoint. *Why:* only a definitive answer may change what the
user may do; a server bug must read as "try again", not "your item is gone".
<!-- origin: arch-spec DATA-12 -->

**DATA-14** — An error body is read once per response, through one helper that returns `null` on any parse
failure; interceptors and authenticators use `peekBody(limit)`, never consume the body. *Why:* the error body
is a single-read stream; a second read silently yields nothing and misclassifies the response.
<!-- origin: arch-spec DATA-14 -->

**DATA-15** — Classification branches on HTTP status first, then refines by error code within that status;
statuses that mean different things to the app ("credentials dead" vs "access refused") map to different
rungs whatever the body says. *Why:* branching on the code alone lets a generic code under the wrong status
fall into the wrong branch.
<!-- origin: arch-spec DATA-15 -->

**DATA-16** — The service's KDoc documents its ladder (status / condition → rung), and unexpected statuses are
logged with `Reporter.error("<endpoint> → <code>", where = logTag)`. *Why:* the ladder is the contract between
backend and app; reviewers and QA need it in one place.
<!-- origin: arch-spec DATA-16 -->

**DATA-17** — An ambiguous failure never ends a session, never discards user input and never drops a pending
write attempt; only a definitive answer may. *Why:* false logouts and lost forms are the most expensive bugs
this layer can produce.
<!-- origin: arch-spec DATA-17 -->

**DATA-18** — Outcomes the host owns (SessionExpired, app-wide scope loss) are swallowed by feature screens:
stop the spinner, show nothing. Feature-specific refusals are rendered by the feature. *Why:* two components
reacting to one event produce double navigation and duplicate banners (UI-17).
<!-- origin: arch-spec DATA-18 -->

**DATA-19** — With refreshable credentials, a service method called both by a timer and by the user lets the
caller say which (a refresh-policy tag); timer-driven calls never extend a session. *Why:* a background poll
that silently refreshes tokens keeps an idle device logged in forever.
<!-- origin: arch-spec DATA-19 -->

```kotlin
/** Ladder (example): 2xx+mappable → Success · 2xx unmappable → Transient · 401 → SessionExpired ·
 *  403 → Refused(code) · 404/410 → Unavailable(GONE) · else → Transient. */
override suspend fun getX(id: String): XFetchResult<X> = withContext(dispatchers.io) {
    try {
        val r = api.getX(id)
        when {
            r.isSuccessful -> r.body()?.toDomain()?.let { XFetchResult.Success(it) }
                ?: XFetchResult.Transient(null).also { Reporter.error("GET x/$id: unusable 2xx", where = logTag) }
            r.code() == 401 -> XFetchResult.SessionExpired
            r.code() == 403 -> XFetchResult.Refused(parseErrorCode(r))
            r.code() == 404 || r.code() == 410 -> XFetchResult.Unavailable(XUnavailableReason.GONE)
            else -> XFetchResult.Transient(null).also { Reporter.error("GET x/$id → ${r.code()}", where = logTag) }
        }
    } catch (e: IOException) { XFetchResult.Transient(e) }
    catch (e: SerializationException) { XFetchResult.Transient(e) }
}
```

## 3. DTOs and mapping

**DATA-20** — Wire types are `@Serializable data class …Dto` in the `dto` package, separate from domain
models; every optional field has a default; the app shares one configured `Json` (unknown keys ignored)
provided once by DI. *Why:* a new or omitted server field must not break an otherwise valid payload.
<!-- origin: arch-spec DATA-20 -->

**DATA-21** — Wire enumerations are `String` in DTOs, converted by a total function
(`entries.firstOrNull { it.wire == code } ?: UNKNOWN`); an unknown value degrades, never throws. *Why:* the
server ships new values before the app does; one unknown value must not fail a whole list.
<!-- origin: arch-spec DATA-21 -->

**DATA-22** — Mapping is `internal fun XDto.toDomain(): X` (or `X?` to drop an item) in the mapper file;
derived values are re-derived from raw fields with shared helpers; data-quality gaps are logged once per id,
not on every poll. *Why:* DTOs do not leak, derived values cannot disagree between screens, logs stay usable.
<!-- origin: arch-spec DATA-22 -->

**DATA-23** — Domain models are plain Kotlin data classes / enums with KDoc per field, no Android types, no
serialization annotations; exact quantities (money, measures) use `BigDecimal`. *Why:* the model must not
change when the wire format does.
<!-- origin: arch-spec DATA-23 -->

**DATA-24** — Infra modules expose raw error codes (`String?`); each feature maps codes to its own vocabulary.
*Why:* a shared module that knows feature codes becomes a dependency magnet every feature must edit.
<!-- origin: arch-spec DATA-24 -->

## 4. Validators

**DATA-30** — Validators are pure and stateless and return typed errors (enums / sealed types) aggregated
into a result with a derived `isValid`; never strings or resource ids. *Why:* typed errors are exhaustively
testable and localised by the composable, which owns resources.
<!-- origin: arch-spec DATA-30 -->

**DATA-31** — The ViewModel validates on submit; a field's error clears when that field changes; input
filtering happens in the `onXChange` intent before state is updated (UI-07). *Why:* errors while typing are
noise; filtering at the intent keeps impossible input out of state.
<!-- origin: arch-spec DATA-31 -->

**DATA-32** — Client validation is advisory: the server's business refusal is still classified (Rejected)
and shown, without field detail if its payload is unrecognised. *Why:* client and server rules drift; the
server's "no" must never be lost.
<!-- origin: arch-spec DATA-32 -->

## 5. Caches and stores

**DATA-40** — Every data holder is one of three kinds, chosen by lifetime: **app-wide cache** (process;
cleared when identity changes; `@Singleton` with its own qualified scope and a `StateFlow` snapshot),
**flow snapshot store** (one user flow; typed store over Scopes keyed by entity id, cleared by the flow
root's `onCleared()`), **persisted store** (across process death; in an infra module). Feature domains hold
in-memory kinds only. *Why:* lifetime is the decision that causes bugs (stale data after logout, leaks, lost
state).
<!-- origin: arch-spec DATA-40 -->

**DATA-41** — A cache is not a source of truth: every consumer tolerates an absent or stale entry and falls
back to one read; after a mutation the authoritative re-read still happens. *Why:* process death, a cleared
scope or a second device can invalidate any in-memory copy at any time.
<!-- origin: arch-spec DATA-41 -->

**DATA-42** — In-memory entries are keyed by entity id and have an explicit owner that clears them. *Why:* two
open items must not see each other's data; an entry with no owner is a leak.
<!-- origin: arch-spec DATA-42 -->

**DATA-43** — Ids → route arguments; large read models → a snapshot store; results → the previous back-stack
entry's saved state. Nothing that must survive process death lives only in Scopes or a store (KERNEL-09).
*Why:* routes and saved state are restored by the system; Scopes is not.
<!-- origin: arch-spec DATA-43 -->

**DATA-44** — An app-wide cache exposes a snapshot in which "not loaded", "stale", "refreshing" and "last
failure" are states, not errors; a failed refresh keeps the last data; staleness is flipped by a scheduled job;
in-session TTLs use the monotonic TimeSource; content is dropped first when the identity changes. *Why:*
screens render from the snapshot alone; showing the previous user's data is a privacy bug.
<!-- origin: arch-spec DATA-44 -->

**DATA-45** — Forced refreshes are single-flight: concurrent callers share one in-flight `Deferred` launched on
the cache's own scope; a user-driven refresh never rides on a background-only request. *Why:* N taps cost one
request, and a waiting user gets the attempt that can actually succeed.
<!-- origin: arch-spec DATA-45 -->

**DATA-46** — Persisted stores never throw on corrupt data: they return `null` / a safe default and
`Reporter.warn` (KERNEL-17). *Why:* a start-up crash loop from one bad byte is unrecoverable for the user.
<!-- origin: arch-spec DATA-46 -->

**DATA-47** — A mutation's response is written back into the relevant snapshot store so the previous screen
repaints on return; that screen's own re-read follows and stays authoritative. *Why:* instant feedback without
trusting the copy.
<!-- origin: arch-spec DATA-47 -->

## 6. Writes

**DATA-50** — Every non-idempotent write carries a request id minted once per attempt (when the user commits
the action) through the injected generator, stored with the exact body, and resent unchanged on every retry
until a definitive answer; a new user action mints a new attempt. *Why:* a resend after a timeout must be
recognised as the same action, not a duplicate write.
<!-- origin: arch-spec DATA-50 -->

**DATA-51** — Transient keeps the attempt (the user's retry resends it); RetryLater triggers a bounded
automatic backoff resending the same attempt, then surfaces as Transient; definitive rungs drop the attempt.
*Why:* "still processing" resolves on its own; unbounded loops do not.
<!-- origin: arch-spec DATA-51 -->

**DATA-52** — Optimistic-concurrency tokens (version, `updatedAt`, ETag) are captured from the state the user
saw and confirmed, stored in the attempt and echoed verbatim; a retry never re-reads them. *Why:* re-reading
commits against a version the user never saw — what the lock exists to prevent.
<!-- origin: arch-spec DATA-52 -->

**DATA-53** — "Status changed" on a read means the item left the user's reach (Unavailable); on a write it is
ambiguous, so the interactor re-reads and resolves to Resynced(fresh) or Unavailable (DATA-04). *Why:* folding
a write conflict into "gone" ejects users from work that is still theirs.
<!-- origin: arch-spec DATA-53 -->

**DATA-54** — At most one write attempt per action is in flight: intents check an in-flight flag; per-row
actions keep `inFlightIds: Set<Id>` and attempts in a map. *Why:* double taps must not send two attempts.
<!-- origin: arch-spec DATA-54 -->

## 7. Concurrency

**DATA-60** — Cancellation propagates from the ViewModel through interactor and service to the HTTP call;
"last action wins" flows cancel the previous job and drop late results with a generation counter (UI-15).
*Why:* a superseded request must neither keep the network busy nor overwrite newer state.
<!-- origin: arch-spec DATA-60 -->

**DATA-61** — Every long-lived scope is `CoroutineScope(SupervisorJob() + dispatcher)`, provided `@Singleton`
under a module-owned qualifier and injected (DI-11). *Why:* one failing child must not kill the scope; tests
must own scheduling.
<!-- origin: arch-spec DATA-61 -->

**DATA-62** — Background machinery that must run from process start (an app-wide cache, an access-loss filter)
is constructed eagerly by the host (DI-14). *Why:* lazy construction makes behaviour depend on navigation
order.
<!-- origin: arch-spec DATA-62 -->
