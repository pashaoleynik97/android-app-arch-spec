# Recommended: access-loss — the 403 chain of responsibility (ACCESS)

Tier: **Recommended** (component `access-loss`, depends on `auth-session`). SHOULD / MAY guidance with a *why*.
No parameters. Related: ADR-0005 (`Channel` for event buses), DATA-11 (`Refused` / `ScopeLost` rungs), DATA-18
(host-owned outcomes), DATA-62 (eager filters).

## When to use

- The backend answers **403** for two different reasons: "you may not do *this*" (one action) and "you lost the
  scope your session is bound to" (an organisation, tenant or workspace — nothing will work any more).
- Background work (polls, caches) can receive those 403s, so the reaction cannot live in the screen that asked.

## When not to

- A backend whose 403 always means one thing to the user: the feature's `Refused` rung is enough.
- No session (the host fallback ends sessions).

## What it adds

In `:network` (package `{{ROOT_PACKAGE}}.network.access`, added without editing existing network code):
`AccessLoss` (`Forbidden(where, code)` / `ScopeLost(where, code)`), `AccessLossBus` + `DefaultAccessLossBus`,
`AccessLossHandler`, `AccessLossFilter`, `AccessLossInterceptor` (contributed to the `ApiInterceptor` set),
`AccessLossConfig`, `RecoveryBudget`, the optional port `ScopeRecovery`, `di/AccessLossModule`. In `:auth`:
`access/AccessLossFallback` (the host's terminal handler). In `:app`: `access/AccessLossHostExtension`,
`di/AccessLossHostModule`, `res/values/access_strings.xml`.

---

**ACCESS-01** — Every 403 on the business client SHOULD be reported by **one** interceptor (observer band),
which peeks the code, maps it to `ScopeLost` when it is in `AccessLossConfig.scopeLevelCodes` and to
`Forbidden` otherwise, and returns the response unchanged.
*Why:* detection in one place cannot be forgotten by a new caller; the unchanged response still reaches the
feature's classifier, which reads the body itself (DATA-14).

**ACCESS-02** — `report` SHOULD be non-blocking (`Channel.trySend`) and the bus SHOULD drain **sequentially** on
an app-lifetime scope, from a `Channel` with a drop-oldest buffer — not a `SharedFlow`.
*Why:* it is called on OkHttp threads; sequential handling stops two failing requests from starting two
competing recoveries; a shared flow drops reports emitted before its collector starts (ADR-0005).

**ACCESS-03** — A loss SHOULD be offered in this order: registered handlers, **last registered first** (the
active screen); then the standing `AccessLossFilter`s; then the host fallback. The first `true` stops the chain;
a throwing handler is logged and skipped (cancellation still propagates).
*Why:* the screen in front knows best whether a refusal is local to it; one broken handler must not swallow
everyone else's chance.

**ACCESS-04** — A feature's **expected** 403s SHOULD be swallowed by a standing filter contributed with
`@Binds @IntoSet AccessLossFilter` (matching on `where` and `code`), not by a per-screen registration.
*Why:* the 403 arrives on a background thread and races a screen-scoped registration; a filter that stands from
the bus's construction never misses one, and the host does not add a generic notice on top of the feature's own
message.

**ACCESS-05** — The host fallback SHOULD always consume: `Forbidden` → a "no permission" notice, the user stays
put; `ScopeLost` → the app's `ScopeRecovery` if one is bound and the budget allows, else
`AuthManager.endSession(ACCESS_CHANGED)` (the session host extension then navigates with its notice).
*Why:* losing the scope the tokens carry is losing the session; staying signed in only earns the next 403.
Moving a user away from a single refused action is worse than the refusal.

**ACCESS-06** — Any **automatic** recovery that mints new tokens SHOULD run inside a sliding-window
`RecoveryBudget` (default 3 per 60 s, monotonic clock), refilled at every session boundary; past the budget the
session ends.
*Why:* a backend that keeps refusing freshly minted tokens loops forever — each round is a new request with a
new token, so no retry cap further down can see it.

**ACCESS-07** — The access notice SHOULD be scoped to a live session: cleared when the session ends, rendered in
its own host layer below the session layer and above connectivity and screen banners.
*Why:* a stale "no permission" notice surfacing on the sign-in screen behind the session notice is noise.

**ACCESS-08** — Features SHOULD still classify the 403 in their own ladder (`Refused(code)` or `ScopeLost`) and
render only `Refused`; `ScopeLost` is swallowed (DATA-18).
*Why:* the bus reports, it does not answer the call — the screen still needs an outcome to stop its spinner.

---

## Adapting this to your app

- **Example contract:** the scope-level code `SCOPE_ACCESS_REVOKED` — set your backend's codes by replacing
  `provideAccessLossConfig()` in `AccessLossModule`. The vocabulary (`Forbidden` / `ScopeLost`) stays.
- **Recovering instead of signing out:** bind one `ScopeRecovery` (`@Binds @IntoSet`) in `:app` or in the feature
  that owns the scope picker; it navigates and returns `true`. The budget still applies.
- **More kinds of loss:** add a subtype of `AccessLoss` and a branch in the fallback; keep the chain order.
- **Keep:** one interceptor, peek-don't-consume, sequential `Channel` drain, LIFO + standing filters + an
  always-consuming fallback, the recovery budget.
