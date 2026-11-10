# Access loss — the 403 chain of responsibility (component: access-loss)

Recommended guidance (SHOULD) from `.arch-spec/spec/specs/recommended/access-loss.md` (`ACCESS-*`), with ADR-0005
(`Channel` for event buses) and DATA-11 / DATA-18 (the `Refused` / `ScopeLost` rungs, host-owned outcomes).
Package `{{ROOT_PACKAGE}}.network.access` (in `:network`), the fallback `AccessLossFallback` (in `:auth`), and the
notice layer `AccessLossHostExtension` (the `access` package of `:app`).

Contents: 1. The chain · 2. Rules · 3. Recipes · 4. Testing · 5. Changing it

---

## 1. The chain

```
any business call ── 403 ──▶ AccessLossInterceptor (peek code) ──▶ AccessLossBus.report(loss)   [non-blocking]
                                                                      │  Channel, drained sequentially
        registered handlers, newest first ◀──────────────────────────┘
        → standing AccessLossFilter set → host fallback (AccessLossFallback, always consumes)
```

`AccessLoss` is `Forbidden(where, code)` (one action refused) or `ScopeLost(where, code)` (the whole scope the
session is bound to — codes listed in `AccessLossConfig.scopeLevelCodes`). The response itself goes back to the
caller unchanged; the feature still classifies it.

## 2. Rules

**ACCESS-01** — Every 403 on the business client is reported by the one `AccessLossInterceptor` (observer band),
which peeks the code and returns the response unchanged. *Why:* detection in one place cannot be forgotten; the
feature's classifier still reads the body (DATA-14).
<!-- origin: arch-spec ACCESS-01 -->

**ACCESS-02** — `report` never blocks (`trySend`); the bus drains a drop-oldest `Channel` sequentially on the
network module's app scope. *Why:* it runs on OkHttp threads; sequential handling prevents two competing
recoveries; a `SharedFlow` would drop reports emitted before its collector started (ADR-0005).
<!-- origin: arch-spec ACCESS-02 -->

**ACCESS-03** — Order: registered handlers (last registered first) → standing filters → host fallback; the first
`true` stops; a throwing handler is logged and skipped, cancellation propagates. *Why:* the screen in front knows
whether a refusal is local; one broken handler must not swallow the chain.
<!-- origin: arch-spec ACCESS-03 -->

**ACCESS-04** — A feature's expected 403s are swallowed by a standing filter, `@Binds @IntoSet AccessLossFilter`
in the feature's domain module — not by a per-screen registration. *Why:* the 403 arrives on a background thread
and races a screen-scoped registration; a standing filter exists from the bus's construction (DATA-62).
<!-- origin: arch-spec ACCESS-04 -->

**ACCESS-05** — The fallback always consumes: `Forbidden` → "no permission" notice, the user stays put;
`ScopeLost` → the bound `ScopeRecovery` (if any, within budget), else
`AuthManager.endSession(SessionEndReason.ACCESS_CHANGED)` — the session host extension then navigates with its
notice. *Why:* losing the scope the tokens carry is losing the session; moving a user away from one refused
action is worse than the refusal.
<!-- origin: arch-spec ACCESS-05 -->

**ACCESS-06** — Automatic recoveries run inside a sliding-window `RecoveryBudget` (default 3 per 60 s,
monotonic), refilled at every session boundary; past the budget the session ends. *Why:* a backend that keeps
refusing fresh tokens would loop forever — every round is a new request, invisible to retry caps.
<!-- origin: arch-spec ACCESS-06 -->

**ACCESS-07** — The access notice belongs to a live session (cleared when it ends) and has its own host layer
(`order = 20`): below the session layer, above connectivity and screen banners. *Why:* a stale "no permission"
under the sign-in screen's session notice is noise.
<!-- origin: arch-spec ACCESS-07 -->

**ACCESS-08** — Features still classify the 403 in their own ladder (`Refused(code)` / `ScopeLost`) and render
only `Refused`; `ScopeLost` is swallowed. *Why:* the bus reports, it does not answer the call — the screen still
needs an outcome to stop its spinner (DATA-18).
<!-- origin: arch-spec ACCESS-08 -->

## 3. Recipes

Swallow a feature's expected refusal (its own screen shows a specific message):

```kotlin
internal class ItemReadOnlyFilter @Inject constructor() : AccessLossFilter {
    override suspend fun handle(loss: AccessLoss) =
        loss is AccessLoss.Forbidden && loss.code == "ITEM_NOT_ALLOWED" && loss.where.startsWith("/v1/items/")
}
// in the feature's domain module:
@Binds @IntoSet abstract fun itemReadOnlyFilter(impl: ItemReadOnlyFilter): AccessLossFilter
```

Recover a lost scope instead of signing out — bind one `ScopeRecovery` in the host (it may see the picker
feature's API, MOD-19):

```kotlin
class PickScopeAgain @Inject constructor(/* the picker's port */) : ScopeRecovery {
    override suspend fun recover(loss: AccessLoss.ScopeLost): Boolean { /* navigate to the picker */ return true }
}
@Binds @IntoSet abstract fun scopeRecovery(impl: PickScopeAgain): ScopeRecovery
```

A screen that wants first refusal while it is in front: inject `AccessLossBus`, `register` in the ViewModel's
init, keep the `Registration`, and `cancel()` it in `onCleared()` — a handler that outlives its screen swallows
losses meant for the host.

Scope-level codes: replace `provideAccessLossConfig()` in `AccessLossModule` (example default
`SCOPE_ACCESS_REVOKED`).

## 4. Testing

- `DefaultAccessLossBusTest` — order (newest registration, filters, fallback), consumption stops the chain,
  cancelled registration, throwing handler, reports made before the drain runs are kept and ordered.
- `AccessLossInterceptorTest` — code → kind, unparseable body → `Forbidden(null)`, body still readable,
  non-403 reports nothing.
- `RecoveryBudgetTest` — burst refused, window slides, reset refills (`FakeTimeSource`).
- `AccessLossFallbackTest` (in `:auth`) — installs itself, notice for `Forbidden`, session ends without a
  recovery, budget exhausts, a dead session clears the notice.
- Device: the `GET_403_scope-lost` scenario under `.mitm_scripts/v1_items_itemId/` (needs a feature that reads
  an item).

Run: `./gradlew :network:testDebugUnitTest :auth:testDebugUnitTest`.

## 5. Changing it

- New kind of loss → a new `AccessLoss` subtype, a branch in `AccessLossFallback.handle`, a row in the tests.
- Keep: one interceptor, peek-don't-consume, sequential `Channel` drain, the chain order, an always-consuming
  fallback, the recovery budget.
